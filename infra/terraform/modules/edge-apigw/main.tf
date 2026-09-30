# Borde de Academy (el Learner Lab bloquea CloudFront): una HTTP API de API Gateway da el HTTPS que exige Entra
# (dominio *.execute-api.amazonaws.com) y un solo origen para la web y la API, sin CORS:
#   ANY /api/{proxy+} → ALB (agrega X-Origin-Verify; el ALB rechaza lo que no la trae)
#   $default          → sitio estático S3 (la SPA; el index.html también responde las rutas del router)
# El bucket de la web es público de solo lectura: solo contiene el build de la SPA, nunca datos.

variable "name" { type = string }
variable "alb_dns_name" { type = string }

variable "bucket_via_cli" {
  description = "true en el Learner Lab: crea el bucket con la AWS CLI (ver modules/s3-bucket)"
  type        = bool
  default     = false
}


variable "origin_secret" {
  type      = string
  sensitive = true
}

variable "throttle_rate" {
  description = "Requests por segundo sostenidos que acepta la API (protección básica sin WAF)"
  type        = number
  default     = 50
}

# ── SPA en S3 (sitio estático) ────────────────────────────────────────────────
data "aws_caller_identity" "current" {}

module "web_bucket" {
  source  = "../s3-bucket"
  name    = "${var.name}-web-${data.aws_caller_identity.current.account_id}"
  use_cli = var.bucket_via_cli # el contenido se regenera en cada build: se borra al destruir
}

resource "aws_s3_bucket_website_configuration" "web" {
  bucket = module.web_bucket.id
  index_document { suffix = "index.html" }
  error_document { key = "index.html" } # rutas del router de React (/app, /ranking, ...)
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = module.web_bucket.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

data "aws_iam_policy_document" "web_public_read" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${module.web_bucket.arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket     = module.web_bucket.id
  policy     = data.aws_iam_policy_document.web_public_read.json
  depends_on = [aws_s3_bucket_public_access_block.web]
}

# ── HTTP API ──────────────────────────────────────────────────────────────────
resource "aws_apigatewayv2_api" "this" {
  name          = var.name
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "api" {
  api_id             = aws_apigatewayv2_api.this.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = "http://${var.alb_dns_name}/api/{proxy}"
  request_parameters = {
    "append:header.X-Origin-Verify" = var.origin_secret
  }
}

resource "aws_apigatewayv2_integration" "web" {
  api_id             = aws_apigatewayv2_api.this.id
  integration_type   = "HTTP_PROXY"
  integration_method = "GET"
  integration_uri    = "http://${aws_s3_bucket_website_configuration.web.website_endpoint}"
  request_parameters = {
    "overwrite:path" = "$request.path"
  }
}

resource "aws_apigatewayv2_route" "api" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /api/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.api.id}"
}

resource "aws_apigatewayv2_route" "web" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.web.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = var.throttle_rate
    throttling_burst_limit = var.throttle_rate * 2
  }
}

output "app_url" { value = aws_apigatewayv2_api.this.api_endpoint }
output "web_bucket" { value = module.web_bucket.id }
