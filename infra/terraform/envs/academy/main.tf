# Entorno AWS Academy (Learner Lab): cómputo temporal para demos.
# Restricciones del Lab que definen este entorno (ver ADR-0002, enmienda 2026-09-28):
#   - No se pueden crear roles IAM → las tasks usan el `LabRole` existente.
#   - CloudFront, AppSync y Cloud Map bloqueados → HTTPS con API Gateway (HTTP API) delante del ALB y
#     de la SPA en S3; partidas en vivo por long polling (ADR-0002, enmienda 2026-09-29); tournament y game
#     llaman a users por el ALB (/internal/*), con X-Internal-Token y la cabecera de origen.
#   - Fargate en x86 (ARM64 no verificado en el lab).
#   - Credenciales que rotan cada ~4 h → todo se recrea con `terraform apply`.
#
# Uso:
#   terraform init -backend-config="bucket=chessquery-tfstate-<account_id>"
#   terraform apply -var-file=academy.tfvars

terraform {
  required_version = ">= 1.10"
  required_providers {
    aws     = { source = "hashicorp/aws", version = "~> 6.0" }
    random  = { source = "hashicorp/random", version = "~> 3.6" }
    archive = { source = "hashicorp/archive", version = "~> 2.7" }
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

  # Servicios desplegados. `pri` menor = regla evaluada antes: tournament y game van antes que users porque
  # users tiene el comodín /api/public/*. /internal/* es servicio→servicio (API Gateway no lo publica).
  services = {
    game = {
      port  = 8083
      paths = ["/api/games", "/api/games/*", "/api/public/games", "/api/public/games/*"]
      pri   = 3
    }
    tournament = {
      port  = 8082
      paths = ["/api/tournaments", "/api/tournaments/*", "/api/public/tournaments", "/api/public/tournaments/*"]
      pri   = 5
    }
    users = {
      port = 8081
      paths = ["/api/users/*", "/api/organizations", "/api/organizations/*", "/api/friends", "/api/friends/*",
      "/api/catalog/*", "/api/public/*", "/internal/*"]
      pri = 10
    }
  }

  # Lo común a los tres servicios Java.
  service_env = {
    DB_URL                                    = "jdbc:postgresql://${module.data.db_endpoint}:5432/${module.data.db_name}"
    DB_USER                                   = module.data.db_username
    OIDC_ISSUER_URI                           = var.oidc_issuer_uri
    OIDC_AUDIENCE                             = var.oidc_audience
    JAVA_TOOL_OPTIONS                         = "-XX:MaxRAMPercentage=75"
    MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED = "true" # health checks del ALB en /actuator/health/liveness
    CHESS_EVENTS_TOPIC_ARN                    = module.messaging.topic_arn
    USERS_URL                                 = "http://${module.alb.dns_name}"
  }
  service_secrets = {
    DB_PASSWORD    = "${module.data.db_master_secret_arn}:password::"
    INTERNAL_TOKEN = module.data.internal_token_param_arn
  }
  queues = module.messaging.queue_names
}

data "aws_iam_role" "lab" {
  name = "LabRole"
}

# ── Red, datos, registro de imágenes y cluster ────────────────────────────────
module "network" {
  source      = "../../modules/network"
  name        = local.name
  alb_ingress = "public" # lo protege la cabecera secreta que agrega API Gateway
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

# ── Entrada: API Gateway (HTTPS) → ALB (/api/*) y S3 (SPA) ────────────────────
resource "random_password" "origin_secret" {
  length  = 40
  special = false
}

# Los servicios que llaman a users por el ALB leen la cabecera de origen desde SSM (no queda en texto plano en
# la definición de la task).
resource "aws_ssm_parameter" "origin_secret" {
  name  = "/${local.name}/origin-secret"
  type  = "SecureString"
  value = random_password.origin_secret.result
}

module "alb" {
  source        = "../../modules/alb"
  name          = local.name
  vpc_id        = module.network.vpc_id
  subnet_ids    = module.network.public_subnet_ids
  alb_sg_id     = module.network.alb_sg_id
  origin_secret = random_password.origin_secret.result
  routes = {
    for k, s in local.services : k => { port = s.port, paths = s.paths, listener_pri = s.pri }
  }
}

module "edge" {
  source        = "../../modules/edge-apigw"
  name          = local.name
  alb_dns_name  = module.alb.dns_name
  origin_secret = random_password.origin_secret.result
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
  target_group_arn   = module.alb.target_group_arns["users"]
  use_spot           = var.use_spot
  cpu_architecture   = "X86_64"

  # Bus SNS/SQS con las credenciales del rol de la task (LabRole), sin contraseñas.
  environment = merge(local.service_env, {
    USERS_ELO_QUEUE    = local.queues["users-elo"]
    USERS_RATING_QUEUE = local.queues["users-rating"]
  })
  secrets = merge(local.service_secrets, { PRIVACY_PEPPER = module.data.privacy_pepper_param_arn })
}

module "tournament" {
  source             = "../../modules/ecs-service"
  name               = "tournament"
  cluster_arn        = aws_ecs_cluster.this.arn
  image              = "${aws_ecr_repository.svc["tournament"].repository_url}:${var.image_tags["tournament"]}"
  port               = local.services.tournament.port
  execution_role_arn = data.aws_iam_role.lab.arn
  task_role_arn      = data.aws_iam_role.lab.arn
  subnet_ids         = module.network.public_subnet_ids
  security_group_ids = [module.network.services_sg_id]
  target_group_arn   = module.alb.target_group_arns["tournament"]
  use_spot           = var.use_spot
  cpu_architecture   = "X86_64"

  environment = merge(local.service_env, {
    TOURNAMENT_FEDERATION_QUEUE = local.queues["tournament-federation"]
    TOURNAMENT_PLAYERS_QUEUE    = local.queues["tournament-players"]
  })
  secrets = merge(local.service_secrets, { ORIGIN_VERIFY = aws_ssm_parameter.origin_secret.arn })
}

# game: 1 réplica y sin Spot. El aviso a los long polls vive en memoria (ADR-0002, enmienda 2026-09-29) y una
# interrupción de Spot cortaría partidas en curso (el reloj sigue en la base de datos, pero se vería un corte).
module "game" {
  source             = "../../modules/ecs-service"
  name               = "game"
  cluster_arn        = aws_ecs_cluster.this.arn
  image              = "${aws_ecr_repository.svc["game"].repository_url}:${var.image_tags["game"]}"
  port               = local.services.game.port
  execution_role_arn = data.aws_iam_role.lab.arn
  task_role_arn      = data.aws_iam_role.lab.arn
  subnet_ids         = module.network.public_subnet_ids
  security_group_ids = [module.network.services_sg_id]
  target_group_arn   = module.alb.target_group_arns["game"]
  use_spot           = false
  desired_count      = 1
  cpu_architecture   = "X86_64"

  environment = local.service_env
  secrets     = merge(local.service_secrets, { ORIGIN_VERIFY = aws_ssm_parameter.origin_secret.arn })
}

# ── ETL: bucket de datos y Lambdas (FIDE mensual, torneos federados diario, fichas pedidas por jugadores) ────
module "etl" {
  source                    = "../../modules/etl-jobs"
  name                      = local.name
  role_arn                  = data.aws_iam_role.lab.arn
  source_dir                = "${path.root}/../../../../etl/chessquery_etl"
  topic_arn                 = module.messaging.topic_arn
  lookup_queue_arn          = module.messaging.queue_arn_by_name["etl-federation-lookup"]
  privacy_pepper_param_name = module.data.privacy_pepper_param_name
}

# ── Bus de eventos (ADR-0002) ─────────────────────────────────────────────────
module "messaging" {
  source          = "../../modules/messaging"
  name            = local.name
  alarm_topic_arn = module.observability.alerts_topic_arn
  consumers       = jsondecode(file("${path.root}/../../../events/topology.json")).consumers
}

module "observability" {
  source                    = "../../modules/observability"
  name                      = local.name
  alert_email               = var.alert_email
  alb_arn_suffix            = module.alb.arn_suffix
  db_identifier             = module.data.db_identifier
  target_group_arn_suffixes = module.alb.target_group_arn_suffixes
}
