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
