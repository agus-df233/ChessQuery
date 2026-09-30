# ALB con enrutamiento por path hacia cada microservicio. Lo usan los dos bordes (CloudFront en la cuenta
# propia, API Gateway en Academy). Si se define `origin_secret`, el ALB solo atiende requests que traen la
# cabecera X-Origin-Verify con ese valor (la agrega el borde): así nadie puede saltarse el borde y pegarle
# directo al ALB aunque su DNS sea público.

variable "name" { type = string }
variable "vpc_id" { type = string }
variable "subnet_ids" { type = list(string) }
variable "alb_sg_id" { type = string }

variable "routes" {
  description = "Servicio → puerto, prefijos de path y health check"
  type = map(object({
    port         = number
    paths        = list(string)
    health_path  = optional(string, "/actuator/health/liveness")
    listener_pri = number
  }))
}

variable "origin_secret" {
  description = "Valor que el borde envía en X-Origin-Verify (null = sin exigir cabecera)"
  type        = string
  default     = null
  sensitive   = true
}

resource "aws_lb" "this" {
  name               = var.name
  load_balancer_type = "application"
  security_groups    = [var.alb_sg_id]
  subnets            = var.subnet_ids
  idle_timeout       = 120 # sobre el long polling de game (25 s) con holgura
}

resource "aws_lb_target_group" "svc" {
  for_each             = var.routes
  name                 = "${var.name}-${each.key}"
  port                 = each.value.port
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = var.vpc_id
  deregistration_delay = 15

  health_check {
    path                = each.value.health_path
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "application/json"
      message_body = "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Ruta no enrutada\"}"
      status_code  = "404"
    }
  }
}

# Un ALB acepta hasta 5 valores de condición por regla y la cabecera de origen ocupa uno: los paths de cada
# servicio se reparten en reglas de a 4 (prioridad = listener_pri * 10 + n° de bloque). Siempre de a 4, con o sin
# cabecera: el tamaño no puede depender de `origin_secret`, que recién se conoce al aplicar (las claves de for_each
# deben conocerse en el plan). Un número de prioridad menor se evalúa antes: los servicios con rutas más específicas
# (p. ej. /api/public/tournaments/*) deben tener listener_pri menor que el que tiene el comodín (/api/public/* de users).
locals {
  rules = merge([
    for svc, r in var.routes : {
      for i, chunk in chunklist(r.paths, 4) :
      "${svc}-${i}" => { service = svc, paths = chunk, priority = r.listener_pri * 10 + i }
    }
  ]...)
}

resource "aws_lb_listener_rule" "svc" {
  for_each     = local.rules
  listener_arn = aws_lb_listener.http.arn
  priority     = each.value.priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.svc[each.value.service].arn
  }

  condition {
    path_pattern { values = each.value.paths }
  }

  dynamic "condition" {
    for_each = var.origin_secret == null ? [] : [var.origin_secret]
    content {
      http_header {
        http_header_name = "X-Origin-Verify"
        values           = [condition.value]
      }
    }
  }
}

output "dns_name" { value = aws_lb.this.dns_name }
output "arn_suffix" { value = aws_lb.this.arn_suffix }
output "target_group_arns" { value = { for k, tg in aws_lb_target_group.svc : k => tg.arn } }
output "target_group_arn_suffixes" { value = { for k, tg in aws_lb_target_group.svc : k => tg.arn_suffix } }
