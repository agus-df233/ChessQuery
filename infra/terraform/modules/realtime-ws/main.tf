# Partidas en vivo por WebSocket (ADR-0002, actualización 30-09-2026): API Gateway WebSocket API, la única opción de
# WebSocket con wss:// que funciona en el Learner Lab (CloudFront y AppSync bloqueados, el ALB sin certificado).
#
#   navegador ──wss://…/live?token=<access token>──► API Gateway ──HTTP──► ALB /internal/ws/{connect,message,disconnect} ──► game
#   game ──postToConnection (LabRole: execute-api:ManageConnections)──► API Gateway ──► navegador
#
# API Gateway agrega a cada llamada la cabecera de origen (el ALB la exige), el token interno (/internal lo exige),
# el id de la conexión y, al conectar, el token del jugador (los navegadores no permiten cabeceras en WebSocket, por
# eso viaja en la query). game valida ese token igual que un Bearer: si responde 401, API Gateway rechaza la conexión.
# Las conexiones viven en la base de game (tabla ws_connection), no en una instancia.

variable "name" { type = string }
variable "alb_dns_name" { type = string }

variable "origin_secret" {
  type      = string
  sensitive = true
}

variable "internal_token" {
  type      = string
  sensitive = true
}

variable "throttle_rate" {
  description = "Mensajes por segundo sostenidos que acepta la API (protección básica sin WAF)"
  type        = number
  default     = 50
}

locals {
  stage = "live"
  # Ruta de API Gateway → endpoint de game. $default recibe los mensajes del cliente (subscribe, ping).
  routes = {
    "$connect"    = "connect"
    "$disconnect" = "disconnect"
    "$default"    = "message"
  }
  common_headers = {
    "integration.request.header.X-Origin-Verify"  = "'${var.origin_secret}'"
    "integration.request.header.X-Internal-Token" = "'${var.internal_token}'"
    "integration.request.header.X-Connection-Id"  = "context.connectionId"
  }
}

resource "aws_apigatewayv2_api" "this" {
  name                       = "${var.name}-live"
  protocol_type              = "WEBSOCKET"
  route_selection_expression = "$request.body.action" # sin rutas por acción: todo cae en $default y game decide
}

resource "aws_apigatewayv2_integration" "route" {
  for_each             = local.routes
  api_id               = aws_apigatewayv2_api.this.id
  integration_type     = "HTTP_PROXY"
  integration_method   = "POST"
  integration_uri      = "http://${var.alb_dns_name}/internal/ws/${each.value}"
  timeout_milliseconds = 10000
  request_parameters = merge(local.common_headers, each.key == "$connect" ? {
    "integration.request.header.X-Ws-Token" = "route.request.querystring.token"
  } : {})
}

resource "aws_apigatewayv2_route" "route" {
  for_each  = local.routes
  api_id    = aws_apigatewayv2_api.this.id
  route_key = each.key
  target    = "integrations/${aws_apigatewayv2_integration.route[each.key].id}"

  # El token llega en la query al conectar; hay que declararlo para poder mapearlo a una cabecera
  dynamic "request_parameter" {
    for_each = each.key == "$connect" ? ["route.request.querystring.token"] : []
    content {
      request_parameter_key = request_parameter.value
      required              = false # sin token, game responde 401 y la conexión se rechaza con un motivo claro
    }
  }
}

resource "aws_apigatewayv2_stage" "live" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = local.stage
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = var.throttle_rate
    throttling_burst_limit = var.throttle_rate * 2
  }

  depends_on = [aws_apigatewayv2_route.route]
}

output "ws_url" {
  description = "URL del WebSocket para la web (VITE_WS_URL)"
  value       = "${aws_apigatewayv2_api.this.api_endpoint}/${local.stage}"
}

output "management_endpoint" {
  description = "Endpoint con el que game empuja mensajes a las conexiones (LIVE_MANAGEMENT_ENDPOINT)"
  value       = "https://${replace(aws_apigatewayv2_api.this.api_endpoint, "wss://", "")}/${local.stage}"
}
