# Infraestructura como código (Terraform)

Decisiones en `docs/adr/0002-despliegue-aws-bajo-costo.md`. Un solo código con dos entornos:

| Entorno | Cuenta | Entrada | IAM | Estado |
|---|---|---|---|---|
| `envs/academy` | AWS Academy Learner Lab (demos temporales) | CloudFront → ALB | `LabRole` existente | ✅ users + web |
| `envs/aws` | Cuenta propia | CloudFront → API Gateway HTTP API (JWT Entra) → Cloud Map | Roles propios + GitHub OIDC | fase 1 |

```
bootstrap/              bucket S3 del estado (una vez por cuenta, estado local)
modules/network         VPC 2 AZ, subnets públicas sin NAT, SGs (ALB solo desde CloudFront)
modules/data            RDS Postgres 16 (password gestionada por RDS), bucket de archivos, SSM
modules/ecs-service     microservicio Fargate ARM64 genérico (Spot/on-demand, logs 14 días)
modules/edge            CloudFront + S3 (SPA, OAC) + ALB con enrutamiento por path
modules/observability   alarmas 5xx / targets no sanos / espacio RDS → SNS email
```

## Levantar Academy (sesión del Learner Lab)

Requisitos: Terraform ≥ 1.10, AWS CLI v2 y Docker (para la imagen). Copiar las credenciales
de "AWS Details" en el perfil `chessquery-academy` y exportar `AWS_PROFILE=chessquery-academy`.

```bash
# 1. Estado remoto (solo la primera vez en la cuenta)
terraform -chdir=infra/terraform/bootstrap init
terraform -chdir=infra/terraform/bootstrap apply

# 2. Infra (completar academy.tfvars a partir del .example)
cd infra/terraform/envs/academy
terraform init -backend-config="bucket=chessquery-tfstate-$(aws sts get-caller-identity --query Account --output text)"
terraform apply -target=aws_ecr_repository.svc -var-file=academy.tfvars   # repos antes que la imagen
# → build y push de la imagen ARM64 de users con el tag de image_tags (Jib, ver pom)
terraform apply -var-file=academy.tfvars

# 3. Web
npm run build -w web
aws s3 sync apps/web/dist "s3://$(terraform output -raw web_bucket)" --delete
aws cloudfront create-invalidation --distribution-id "$(terraform output -raw cloudfront_distribution_id)" --paths '/*'
```

Luego agregar `$(terraform output -raw app_url)/app` como redirect URI de la SPA en Entra
(en `envs/aws` lo hará el provider `azuread`).

## Apagar para ahorrar saldo

```bash
aws ecs update-service --cluster chessquery-academy --service users --desired-count 0
aws rds stop-db-instance --db-instance-identifier chessquery-academy
# o todo:
terraform destroy -var-file=academy.tfvars
```

El día de una demo usar `use_spot = false` (Fargate on-demand, sin interrupciones) y
encender RDS con al menos 15 minutos de anticipación.
