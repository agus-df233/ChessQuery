# Infraestructura como código (Terraform)

Decisiones en `docs/adr/0002-despliegue-aws-bajo-costo.md` (incluida la enmienda del 28-09-2026). Un solo
código con dos entornos:

| Entorno | Cuenta | Entrada (HTTPS) | IAM | Estado |
|---|---|---|---|---|
| `envs/academy` | AWS Academy Learner Lab | API Gateway HTTP API → ALB (`/api`) y S3 (web) | `LabRole` existente | `plan` OK (57 recursos), falta `apply` |
| `envs/aws` | Cuenta propia | CloudFront → ALB y S3 privado | Roles propios + GitHub OIDC | pendiente |

```
bootstrap/              bucket S3 del estado (una vez por cuenta, estado local)
modules/network         VPC 2 AZ, subnets públicas sin NAT, SGs (ingreso al ALB: cloudfront | public)
modules/data            RDS Postgres 16 (password gestionada por RDS), bucket de archivos, SSM (token, pepper)
modules/ecs-service     microservicio Fargate genérico (ARM64 | X86_64, Spot/on-demand, logs 14 días)
modules/alb             ALB por path; exige la cabecera X-Origin-Verify que agrega el borde
modules/edge            borde cuenta propia: CloudFront + S3 privado (OAC)
modules/edge-apigw      borde Academy: HTTP API + S3 sitio estático (el lab bloquea CloudFront)
modules/messaging       SNS chess-events → SQS por consumidor (filter policy, raw, DLQ + alarma)
modules/observability   alarmas 5xx / targets no sanos / espacio RDS → SNS email
```

## Qué bloquea el Learner Lab (verificado el 28-09-2026)

CloudFront, AppSync y Cloud Map: `AccessDenied`. Permitidos: API Gateway v2, S3 (sin bloqueo público de
cuenta), SNS/SQS, Lambda, Scheduler, RDS (db.t4g.micro, PG 16), ECS/ECR, `LabRole`. La cuenta del lab es
compartida con otro proyecto (`duocconecta`): todos los recursos de acá llevan el prefijo `chessquery-academy`.

## Levantar Academy (lo ejecuta una persona)

Requisitos: Terraform ≥ 1.10, AWS CLI v2, Docker, Node 20. Credenciales del lab en el perfil `default`
(o `ACADEMY_PROFILE=<perfil>`). Copiar `envs/academy/academy.tfvars.example` a `academy.tfvars` y completar.

```bash
make academy-bootstrap           # 1 vez por cuenta: bucket del estado
make academy-plan                # revisar: ~57 recursos a crear, nada a destruir
make academy-apply               # crea red, RDS, ECR, ECS, ALB, API Gateway, SNS/SQS, alarmas (~15 min por RDS)
make academy-image               # build x86 con Jib y push a ECR con el tag del commit
make academy-web                 # build de la web y publicación en S3; imprime la URL HTTPS
```

Luego, en Entra External ID, agregar `<app_url>/app` como redirect URI de la SPA. Sin Entra, la web pública
(`/ranking`) y la API pública funcionan igual; el login no.

## Apagar para ahorrar saldo

```bash
make academy-down                # ECS en 0 y RDS detenida (no borra nada)
make academy-destroy             # borra todo lo de este entorno
```

El día de una demo usar `use_spot = false` en `academy.tfvars` (Fargate on-demand) y encender RDS con al
menos 15 minutos de anticipación.

## Verificar sin aplicar (lo que hizo el agente)

`terraform plan` contra el lab con backend local (copia en un directorio temporal con `backend "local" {}`
como override) para detectar permisos y errores antes del `apply`. Encontró y se corrigió un `for_each`
que dependía de un valor conocido recién al aplicar (alarmas de DLQ).
