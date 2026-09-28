# Entorno AWS Academy (Learner Lab): cómputo temporal para demos.
# Restricciones del Lab que definen este entorno (ver ADR-0002):
#   - No se pueden crear roles IAM → las tasks usan el `LabRole` existente.
#   - Cloud Map bloqueado → entrada por ALB (no API Gateway + VPC Link).
#   - Credenciales que rotan cada ~4 h → todo se recrea con `terraform apply`.
#
# Uso:
#   terraform init -backend-config="bucket=chessquery-tfstate-<account_id>"
#   terraform apply -var-file=academy.tfvars

terraform {
  required_version = ">= 1.10"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 6.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
  backend "s3" {
    key          = "envs/academy/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
  }
}

provider "aws" {
  region = "us-east-1"
  default_tags {
    tags = { Project = "chessquery", Environment = local.name }
  }
}

locals {
  name = "chessquery-academy"

  # Servicios desplegados. Se agregan tournament, game y notifications a medida que existan.
  services = {
    users = {
      port  = 8081
      paths = ["/api/users/*", "/api/organizations/*", "/api/friends/*", "/api/catalog/*", "/api/public/*"]
      pri   = 10
    }
  }
}

data "aws_iam_role" "lab" {
  name = "LabRole"
}

# ── Red, datos, registro de imágenes y cluster ────────────────────────────────
module "network" {
  source = "../../modules/network"
  name   = local.name
}

module "data" {
  source     = "../../modules/data"
  name       = local.name
  subnet_ids = module.network.public_subnet_ids
  db_sg_id   = module.network.db_sg_id
}

resource "aws_ecr_repository" "svc" {
  for_each             = local.services
  name                 = "chessquery/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_lifecycle_policy" "svc" {
  for_each   = aws_ecr_repository.svc
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Conservar las últimas 5 imágenes"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 5 }
      action       = { type = "expire" }
    }]
  })
}

resource "aws_ecs_cluster" "this" {
  name = local.name
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
}

# ── Entrada: CloudFront + S3 (SPA) + ALB (/api/*) ─────────────────────────────
module "edge" {
  source     = "../../modules/edge"
  name       = local.name
  vpc_id     = module.network.vpc_id
  subnet_ids = module.network.public_subnet_ids
  alb_sg_id  = module.network.alb_sg_id
  routes = {
    for k, s in local.services : k => { port = s.port, paths = s.paths, listener_pri = s.pri }
  }
}

# ── Servicios ─────────────────────────────────────────────────────────────────
module "users" {
  source             = "../../modules/ecs-service"
  name               = "users"
  cluster_arn        = aws_ecs_cluster.this.arn
  image              = "${aws_ecr_repository.svc["users"].repository_url}:${var.image_tags["users"]}"
  port               = local.services.users.port
  execution_role_arn = data.aws_iam_role.lab.arn
  task_role_arn      = data.aws_iam_role.lab.arn
  subnet_ids         = module.network.public_subnet_ids
  security_group_ids = [module.network.services_sg_id]
  target_group_arn   = module.edge.target_group_arns["users"]
  use_spot           = var.use_spot

  environment = {
    DB_URL            = "jdbc:postgresql://${module.data.db_endpoint}:5432/${module.data.db_name}"
    DB_USER           = module.data.db_username
    OIDC_ISSUER_URI   = var.oidc_issuer_uri
    OIDC_AUDIENCE     = var.oidc_audience
    JAVA_TOOL_OPTIONS = "-XX:MaxRAMPercentage=75"
    # Health checks del ALB contra /actuator/health/liveness.
    MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED = "true"
    # Bus SNS/SQS: credenciales por el rol de la task (LabRole), sin contraseñas.
    CHESS_EVENTS_TOPIC_ARN = module.messaging.topic_arn
    USERS_ELO_QUEUE        = module.messaging.queue_names["users-elo"]
    USERS_RATING_QUEUE     = module.messaging.queue_names["users-rating"]
  }

  secrets = {
    DB_PASSWORD    = "${module.data.db_master_secret_arn}:password::"
    INTERNAL_TOKEN = module.data.internal_token_param_arn
  }
}

# ── Bus de eventos (ADR-0002) ─────────────────────────────────────────────────
module "messaging" {
  source          = "../../modules/messaging"
  name            = local.name
  alarm_topic_arn = module.observability.alerts_topic_arn
  consumers = {
    "users-elo"    = ["elo.updated"]
    "users-rating" = ["rating.updated"]
  }
}

module "observability" {
  source                    = "../../modules/observability"
  name                      = local.name
  alert_email               = var.alert_email
  alb_arn_suffix            = module.edge.alb_arn_suffix
  db_identifier             = module.data.db_identifier
  target_group_arn_suffixes = module.edge.target_group_arn_suffixes
}
