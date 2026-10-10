# ADR-0002 — Despliegue AWS de bajo costo: SNS/SQS, API Gateway, AppSync Events y Terraform en dos cuentas

- **Estado:** Propuesta (2026-09-28)
- **Reemplaza parcialmente a:** ADR-0001 §2 (ALB + WAF como entrada), §5 (relay STOMP sobre RabbitMQ) y §6 (Amazon MQ)
- **Decide:** Agustín Garrido, Martín Mora

## Contexto

La v2 se desplegó en AWS Academy a mano: una sola task Fargate de 4 vCPU / 8 GB con diez
contenedores por `localhost` (Academy bloquea Cloud Map), RabbitMQ y Redis dentro de la task, IP
pública cambiante y credenciales que rotan cada ~4 h. Nada era reproducible y el costo on-demand
rondaba los US$144/mes.

La v3 debe: mantener el IdaaS (Entra External ID), ser cloud-native en AWS, no guardar contraseñas,
costar poco y poder desplegarse tanto en **Learner Lab** (cómputo temporal para demos, sin permisos
IAM) como en una **cuenta AWS propia**. Además, debe sostener las demos del torneo All In Chile 2026
(Midterm 5-nov, Final 13-nov).

## Decisión

1. **Mensajería: SNS + SQS en lugar de Amazon MQ.** Un tópico SNS `chess-events` y una cola SQS
   por servicio consumidor (`<servicio>-<dominio>`), suscrita con *filter policy* sobre el atributo
   `eventType`, y una DLQ por cola (`maxReceiveCount = 5`). Se mantiene el envelope `ChessEvent` y
   la idempotencia con `processed_event`. La topología vive en Terraform, no en el código
   (desaparece `UsersRabbitConfig`). Costo ≈ US$0–1/mes frente a ~US$25 fijos de Amazon MQ, y sin
   contraseña de broker.
2. **Entrada HTTP.** En la cuenta propia: CloudFront → **API Gateway HTTP API** con *authorizer* JWT
   de Entra y throttling → VPC Link → Cloud Map → ECS. Reemplaza ALB + WAF (~US$25/mes). En Academy
   (sin Cloud Map): CloudFront → **ALB** con enrutamiento por path. Los servicios siguen siendo resource
   servers (defensa en profundidad).
3. **Tiempo real: AppSync Events** (antes plan B) para `/games/{id}`, `/tournaments/{id}` y
   `/users/{id}`. Auth OIDC con Entra para suscribirse; solo el rol IAM de `game` publica. REST sigue
   siendo la fuente de verdad, con reloj autoritativo en el servidor. Fallback: STOMP *simple broker*
   con una réplica detrás del ALB, detrás del puerto `EventBroadcaster`.
4. **Cómputo:** ECS Fargate ARM64, un servicio por microservicio (0.5 vCPU / 1 GB), Spot fuera de
   los días de demo y apagado programado con EventBridge Scheduler. **Lambda** solo para cargas
   cortas o programadas: ETL FIDE/Federación, sync nocturno Lichess/Chess.com y envío de emails.
5. **Secretos:** sin contraseñas de usuario (Entra + PKCE). Configuración en SSM Parameter Store
   (SecureString, gratis); RDS con autenticación IAM donde el rol lo permita; GitHub → AWS por
   OIDC en la cuenta propia. `INTERNAL_TOKEN` es el único secreto compartido.
6. **IaC con Terraform**, un solo código con dos entornos: `envs/academy` (`LabRole`, ALB) y
   `envs/aws` (roles propios, API Gateway, OIDC, Budgets). El provider `azuread` versiona las app
   registrations de Entra (redirect URIs = dominio de CloudFront).
7. **Entra External ID se mantiene como excepción no AWS:** ya está integrado, su tier gratuito
   cubre la escala prevista y migrar a Cognito no aporta valor en este horizonte.

## Consecuencias

- Hay que portar `EventPublisher` y los `@RabbitListener` a Spring Cloud AWS (`@SqsListener`) y,
  en local, reemplazar RabbitMQ/MinIO por LocalStack (o ElasticMQ + MinIO).
- SQS entrega "al menos una vez" y sin orden global: los consumidores ya son idempotentes; si un
  flujo exige orden, se evalúa una cola FIFO para ese caso.
- Aparecen dos modos de entrada (ALB/API Gateway) que el módulo `edge` debe mantener.
- Costo estimado: ~US$35/mes con `users` 24/7 y ~US$30–45/mes con los cuatro servicios en Spot
  y apagado fuera de horario; unos US$0.5–0.7 por sesión de 4 h en Learner Lab.

## Enmienda — 28-09-2026: restricciones reales del Learner Lab

Verificado con las credenciales del lab: **CloudFront, AppSync y Cloud Map están bloqueados** (AccessDenied).
API Gateway v2, S3 (sin bloqueo público de cuenta), SNS/SQS, Lambda, Scheduler, RDS y ECS sí están permitidos.

Decisiones para `envs/academy` (la cuenta propia no cambia):
1. **Entrada HTTPS con API Gateway (HTTP API)**, no CloudFront: dominio `*.execute-api.amazonaws.com` con TLS
   (Entra exige HTTPS en los redirect URIs). `ANY /api/{proxy+}` → ALB; `$default` → sitio estático S3 de la SPA.
   Un solo origen: sin CORS.
2. **El ALB exige la cabecera `X-Origin-Verify`** (secreto generado por Terraform) que agrega el borde: como API
   Gateway no tiene rangos IP fijos, el puerto 80 queda abierto y esa cabecera es la que impide saltarse el borde.
   En la cuenta propia la misma cabecera se suma al filtro por prefix list de CloudFront.
3. **Tiempo real con el fallback STOMP** (simple broker, 1 réplica detrás del ALB); AppSync solo en la cuenta propia.
4. **Fargate x86 en Academy** (ARM64 no verificado en el lab); ARM64 en la cuenta propia.
5. El bucket de la web en Academy es público de solo lectura: contiene únicamente el build de la SPA.

Validado con `terraform plan` contra el lab (57 recursos, sin errores de permisos) antes de cualquier `apply`.

## Enmienda — 29-09-2026: tiempo real de las partidas por long polling

El MVP de partidas (`services/game`) usa **long polling sobre la misma API REST** en vez de STOMP o AppSync:
`GET /api/games/{id}?afterVersion=n` queda en espera (hasta 25 s) y responde apenas la partida cambia (jugada,
tablas, abandono, tiempo). Cada cambio sube `version`; el cliente vuelve a preguntar con la versión nueva.

Por qué:
1. **API Gateway HTTP API no transporta WebSocket** (sería otra API de tipo WebSocket, con otro modelo de
   integración): el long polling pasa por la entrada de Academy sin cambios y detrás del ALB en ambas cuentas.
2. El **reloj es del servidor**: un barrido cada segundo cierra por tiempo aunque nadie esté conectado, así que la
   conexión en vivo solo sirve para enterarse antes, no para arbitrar.
3. La espera usa hilos asíncronos del servlet (`DeferredResult`), no bloquea el pool de peticiones.

Límite conocido: el aviso de cambios vive en memoria de cada instancia. Con varias réplicas, un cliente atendido
por otra réplica se entera al vencer su espera (≤ 25 s) o en su siguiente consulta; la API REST sigue siendo la
fuente de verdad. Con 1 réplica (Academy y MVP) el aviso es inmediato. Si se escala, el reemplazo es AppSync Events
en la cuenta propia (ya decidido arriba) publicando desde el mismo punto (`GameNotifier`), sin tocar pantallas.

## Enmienda — 29-09-2026: CI, Trivy y Dependabot

Venían en el esqueleto del proyecto sin una decisión escrita; se revisan y quedan declarados:

1. **El CI corre en cada push a `main` y a `develop` y en cada PR**: Java (tests + cobertura ≥ 90 %), ETL, web
   (Vitest + axe + build), Terraform (fmt + validate), complejidad (informativo) y Trivy.
2. **Trivy** (gratis, de Aqua Security): escanea las dependencias (bloquea con vulnerabilidades HIGH/CRITICAL que
   ya tengan corrección) y la configuración de Terraform (informativo, por las concesiones de costo de Academy).
   La acción se fija a un **commit exacto** y no a una etiqueta: en marzo de 2026 las etiquetas de
   `aquasecurity/trivy-action` se reescribieron y la versión que usábamos (`0.28.0`) desapareció, lo que rompió el CI.
3. **Dependabot**: un PR semanal agrupado por ecosistema (Maven y GitHub Actions) contra `develop`, sin saltos de
   versión mayor (se evalúan a mano, p. ej. Spring Boot 4). Se quitó el escaneo de Docker: `/infra` no tiene
   Dockerfile (las imágenes locales se fijan en `docker-compose.yml` y las de los servicios las arma Jib).
4. El CI tiene solo permiso de lectura sobre el repositorio.

**Actualización 30-09-2026:** se probó en el Learner Lab que **API Gateway WebSocket API sí funciona** (crear la API,
conectarse por `wss://` y empujar mensajes desde el `LabRole`); evidencia en
`docs/verificacion/2026-09-30-websocket-learner-lab.md`. El long polling sigue como mecanismo actual y como respaldo;
el WebSocket por API Gateway queda como el camino para el tiempo real también en el lab.

## Enmienda — 07-10-2026: las Lambdas del ETL reciben los eventos directo de SNS

Se elimina la cola `etl-federation-lookup` (y su DLQ). La Lambda `federation-lookup` queda **suscrita al tópico
`chess-events`** con el mismo filtro por `eventType`, sin SQS de por medio:

1. **Por qué:** una Lambda ya trae lo que la cola aportaba (reintentos y registro de fallas), así que la cola solo
   sumaba piezas que mantener y que limpiar a mano en cada lab. Las colas de los servicios Java se mantienen: ahí
   SQS sí aporta el desacople y la DLQ, porque un servicio puede estar apagado (`academy-down`) y no debe perder eventos.
2. **Fallas:** la invocación es asíncrona; si falla, Lambda la reintenta 2 veces (hasta 1 h después del pedido) y la
   alarma `<lambda>-errores` avisa al tópico de alertas. Un pedido de ficha perdido no rompe nada: el jugador puede
   volver a pedirlo.
3. **Topología:** `infra/events/topology.json` tiene una sección nueva, `lambdas` (Lambda → eventTypes). La leen el
   módulo `etl-jobs` (suscripción `lambda` + permiso para SNS) y, en local, `etl/chessquery_etl/local_bus.py`: un
   receptor HTTP que se suscribe al tópico de LocalStack y llama al **mismo handler** que la Lambda.
4. La futura Lambda de ratings externos (Lichess y Chess.com) usa el mismo mecanismo.

## Enmienda — 07-10-2026: WebSocket de las partidas en la nube y apagado nocturno

1. **Partidas en vivo por API Gateway WebSocket** (módulo `realtime-ws`), tras la prueba del 30-09. El navegador se
   conecta a `wss://…/live?token=<access token>`. API Gateway llama por HTTP al ALB en `/internal/ws/{connect,message,
   disconnect}`, que va a `game`, agregando la cabecera de origen, el token interno, el id de la conexión y, al
   conectar, el token del jugador. `game` valida ese token igual que un Bearer (401 = conexión rechazada) y empuja cada
   jugada con `postToConnection` usando el `LabRole`. El long polling queda como respaldo automático en la web.
   Plan B si la integración HTTP directa diera problemas: una Lambda proxy (`AWS_PROXY`, la variante ya probada).
2. **Apagado nocturno automático** (módulo `apagado-nocturno`): una Lambda con el `LabRole`, disparada por una regla
   de EventBridge a las 23:00 de Chile, deja ECS en 0 y detiene RDS. Cuida el crédito del lab nuevo (US$50) ante un
   olvido; `make academy-up` lo vuelve a encender. Reemplaza el apagado con EventBridge Scheduler de la decisión
   original, que necesita un rol propio que el lab no deja crear.

## Enmienda — 09-10-2026: login con Google por Amazon Cognito en el Learner Lab

**Contexto:**

- No hay tenant de Entra External ID disponible: el directorio de Duoc bloquea crear uno y la prueba gratuita de la
  cuenta personal ya se usó.
- El tenant que sí existe es de personal (*workforce*): con Google solo admite invitados B2B, no registro abierto.

**Decisión:** el IdP del Learner Lab es un **user pool de Amazon Cognito** (plan Lite) con **Google federado**,
creado por Terraform (módulo `auth-cognito`). Entra External ID queda como opción (`auth_provider = "entra"`) para la
cuenta propia.

1. **Login.**
   - La web usa Authorization Code + PKCE contra el dominio de Cognito, con `identity_provider=Google`, que salta
     directo a Google.
   - No hay contraseñas: el cliente solo admite Google y el autorregistro con contraseña está apagado. Los usuarios
     federados se crean en su primer ingreso.
2. **Token hacia la API: el ID token.** En el plan Lite, el access token de Cognito lleva `client_id` y no trae
   `aud` ni el correo; agregarlos exige un plan pagado. El ID token sí trae `aud` (client id de la web), `email`,
   `email_verified` y el nombre.
   - Es el mismo token que acepta el autorizador de Cognito de API Gateway.
   - Los servicios no cambian: validan el issuer del pool y la audiencia con la misma configuración (`OIDC_ISSUER_URI`,
     `OIDC_AUDIENCE`).
   - El WebSocket recibe el mismo token.
3. **Secreto de Google.** Llega a Terraform por `TF_VAR_google_client_secret`, que el Makefile toma del llavero de
   macOS; nunca entra al repo. Queda en el estado de Terraform, que está cifrado y es privado.
4. **Cierre de sesión.** Va al `/logout` de Cognito, con `client_id` y `logout_uri`.
5. **Prueba local con el login real.** `make academy-auth` crea solo el login; `make dev-idp` levanta la app local
   contra ese user pool, porque `http://localhost:5173/app` también es callback del cliente.
