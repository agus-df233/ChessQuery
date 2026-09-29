# Un microservicio Java en Fargate ARM64: task definition, servicio y log group. Genérico para
# users, tournament, game y notifications. Los roles se reciben por variable para que el mismo
# módulo sirva con `LabRole` (Academy) o con roles propios (cuenta propia).

variable "name" { type = string }
variable "cluster_arn" { type = string }
variable "image" { type = string }
variable "port" { type = number }
variable "execution_role_arn" { type = string }
variable "task_role_arn" { type = string }
variable "subnet_ids" { type = list(string) }
variable "security_group_ids" { type = list(string) }

variable "cpu" {
  type    = number
  default = 512
}

variable "memory" {
  type    = number
  default = 1024
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "cpu_architecture" {
  description = "ARM64 (Graviton, ~20 % más barato) en la cuenta propia; X86_64 en Academy (ARM64 no verificado en el lab)"
  type        = string
  default     = "ARM64"
}

variable "use_spot" {
  description = "FARGATE_SPOT fuera de los días de demo; false = on-demand (sin riesgo de interrupción)"
  type        = bool
  default     = true
}

variable "environment" {
  type    = map(string)
  default = {}
}

variable "secrets" {
  description = "Nombre de variable → ARN (SSM o Secrets Manager, con sufijo :clave:: para JSON)"
  type        = map(string)
  default     = {}
}

variable "target_group_arn" {
  type    = string
  default = null
}

variable "log_retention_days" {
  type    = number
  default = 14
}

data "aws_region" "current" {}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name}"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_task_definition" "this" {
  family                   = var.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.task_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([{
    name         = var.name
    image        = var.image
    essential    = true
    portMappings = [{ containerPort = var.port, protocol = "tcp" }]
    environment  = [for k, v in merge({ PORT = tostring(var.port) }, var.environment) : { name = k, value = v }]
    secrets      = [for k, v in var.secrets : { name = k, valueFrom = v }]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.this.name
        awslogs-region        = data.aws_region.current.region
        awslogs-stream-prefix = var.name
      }
    }
  }])
}

resource "aws_ecs_service" "this" {
  name                              = var.name
  cluster                           = var.cluster_arn
  task_definition                   = aws_ecs_task_definition.this.arn
  desired_count                     = var.desired_count
  enable_execute_command            = true
  health_check_grace_period_seconds = var.target_group_arn == null ? null : 120

  capacity_provider_strategy {
    capacity_provider = var.use_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = var.security_group_ids
    assign_public_ip = true
  }

  dynamic "load_balancer" {
    for_each = var.target_group_arn == null ? [] : [var.target_group_arn]
    content {
      target_group_arn = load_balancer.value
      container_name   = var.name
      container_port   = var.port
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # `desired_count` lo cambian también el encendido/apagado programado y `make demo-down`.
  lifecycle {
    ignore_changes = [desired_count]
  }
}

output "service_name" { value = aws_ecs_service.this.name }
output "log_group" { value = aws_cloudwatch_log_group.this.name }
