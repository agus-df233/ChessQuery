# Infraestructura como código (Terraform)

Decisiones en `docs/adr/0002-despliegue-aws-bajo-costo.md` (incluida la enmienda del 28-09-2026). Un solo
código con dos entornos:

| Entorno | Cuenta | Entrada (HTTPS) | IAM | Estado |
|---|---|---|---|---|
| `envs/academy` | AWS Academy Learner Lab | API Gateway HTTP API → ALB (`/api`) y S3 (web); login con Cognito + Google | `LabRole` existente | Desplegado en el lab nuevo (10-2026) |
| `envs/aws` | Cuenta propia | CloudFront → ALB y S3 privado | Roles propios + GitHub OIDC | pendiente |

```
bootstrap/              bucket S3 del estado (una vez por cuenta, estado local)
modules/network         VPC 2 AZ, subnets públicas sin NAT, SGs (ingreso al ALB: cloudfront | public)
modules/data            RDS Postgres 16 (password gestionada por RDS), bucket de archivos, SSM (token, pepper)
modules/ecs-service     microservicio Fargate genérico (ARM64 | X86_64, Spot/on-demand, logs 14 días, rollback automático)
modules/alb             ALB por path; exige la cabecera X-Origin-Verify que agrega el borde
modules/edge            borde cuenta propia: CloudFront + S3 privado (OAC)
modules/edge-apigw      borde Academy: HTTP API + S3 sitio estático (el lab bloquea CloudFront)
modules/messaging       SNS chess-events → SQS por consumidor de los servicios Java (filter policy, raw, DLQ + alarma)
modules/observability   alarmas 5xx / targets no sanos / espacio RDS → SNS email
modules/etl-jobs        bucket del ETL + Lambdas fide-import (mensual), federation-tournaments (diaria),
                        federation-lookup y external-ratings (SNS directo, sin cola) + alarma de errores por Lambda
modules/realtime-ws     WebSocket de API Gateway para las partidas en vivo → ALB /internal/ws/* → game
modules/apagado-nocturno Lambda + regla de EventBridge: a las 23:00 (Chile) ECS en 0 y RDS detenida
modules/auth-cognito    login con Google: user pool (plan Lite), dominio, Google federado y cliente de la web (PKCE)
```

## Qué bloquea el Learner Lab (verificado el 28-09-2026)

CloudFront, AppSync y Cloud Map: `AccessDenied`. Permitidos: API Gateway v2, S3 (sin bloqueo público de
cuenta), SNS/SQS, Lambda, Scheduler, RDS (db.t4g.micro, PG 16), ECS/ECR, `LabRole`. La cuenta del lab es
compartida con otro proyecto (`duocconecta`): todos los recursos de acá llevan el prefijo `chessquery-academy`.

## Levantar Academy (lo ejecuta una persona)

Requisitos: Terraform ≥ 1.10, AWS CLI v2, Node 20, JDK 21 + Maven y Docker (solo para el login a ECR; las imágenes las arma Jib).
Credenciales del lab en el perfil `default` (o `ACADEMY_PROFILE=<perfil>`). Copiar
`envs/academy/academy.tfvars.example` a `academy.tfvars` y completar el client ID de Google. El secreto de Google va al
llavero de macOS y no a un archivo (`docs/auth/cognito-google.md`).

```bash
make academy-bootstrap           # 1 vez por cuenta: bucket del estado
make academy-auth                # solo el login (Cognito); permite probar Google en local con make dev-idp
make academy-plan                # revisar: solo "to add", nada de "to destroy"
make academy-ecr                 # solo los repositorios de imágenes
make academy-image               # 3 imágenes x86 con el tag del commit (queda anotado en envs/academy/.image-tag)
make academy-apply               # red, RDS, ECS, ALB, API Gateway, SNS/SQS y Lambdas (~15 min por RDS)
make academy-web                 # build de la web (login y WebSocket desde las salidas) y publicación en S3
make cloud-smoke                 # pruebas de humo sin login contra lo desplegado
```

**Por qué las imágenes van antes del `apply` completo:** los servicios ECS tienen rollback automático
(`deployment_circuit_breaker`). Si se crean sin imagen en ECR, el primer despliegue queda fallido y hay que
forzar uno nuevo. `academy-apply` usa el tag de la última `academy-image` (`.image-tag`), así que un commit
posterior no apunta a imágenes que no existen; para forzar otro: `make academy-apply IMAGE_TAG=<tag>`.

`<app_url>/app` ya queda registrada como callback del cliente de Cognito: no hay pasos manuales en el IdP. Para cargar
datos reales, invocar las Lambdas del ETL (`chessquery-academy-federation-tournaments`); la lista FIDE se importa
desde un computador porque FIDE no acepta conexiones desde AWS (ADR-0002).

## Apagar para ahorrar saldo

```bash
make academy-down                # ECS en 0 y RDS detenida (no borra nada)
make academy-up                  # lo inverso: enciende RDS, espera que esté disponible y deja los servicios en 1
make academy-destroy             # borra todo lo de este entorno
```

**Apagado automático:** aunque nadie corra `academy-down`, la Lambda `chessquery-academy-apagado-nocturno` lo hace todas
las noches a las 23:00 (Chile). Con todo encendido el lab gasta del orden de US$2,5–3,5 al día; apagado, cerca de US$1
(quedan el ALB, sus IP públicas, el disco de RDS y el secreto). Al día siguiente: `make academy-up`.

El día de una demo usar `use_spot = false` en `academy.tfvars` (Fargate on-demand) y encender RDS con al
menos 15 minutos de anticipación.

## Verificar sin aplicar (lo que hizo el agente)

`terraform plan` contra el lab con backend local (copia en un directorio temporal con `backend "local" {}`
como override) para detectar permisos y errores antes del `apply`. Encontró y se corrigió un `for_each`
que dependía de un valor conocido recién al aplicar (alarmas de DLQ).
