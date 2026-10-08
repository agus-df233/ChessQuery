# ETL en la nube: bucket de datos (raw/ se borra a los 30 días; staged/ y manifests/ quedan para el diff del mes
# siguiente) y tres Lambdas empaquetadas desde etl/chessquery_etl (sin dependencias extra: boto3 viene en el runtime).
#   - fide-import            mensual (día 2): lista FIDE (CHI) → rating.updated
#   - federation-tournaments diaria: torneos de la Federación → federation.tournament.published
#   - federation-lookup      por evento (SNS directo, sin cola): ficha de un jugador que la vinculó → rating.updated
#   - external-ratings       por evento (SNS directo): ratings públicos de Lichess y Chess.com → rating.updated
# Los horarios usan reglas de EventBridge (no Scheduler): no necesitan un rol propio, que el Learner Lab no deja crear.
# Las Lambdas que reaccionan a eventos se suscriben al tópico chess-events con filtro por eventType (sección
# "lambdas" de infra/events/topology.json). Si una invocación falla, Lambda la reintenta 2 veces y la alarma avisa.

variable "name" { type = string }
variable "role_arn" { type = string }
variable "source_dir" { type = string }
variable "topic_arn" { type = string }

variable "subscriptions" {
  description = "Lambda → eventTypes que SNS le entrega directo (sección lambdas de topology.json)"
  type        = map(list(string))
}

variable "alarm_topic_arn" {
  description = "Tópico SNS de alertas al que avisan las alarmas de errores"
  type        = string
  default     = null
}

variable "error_alarms" {
  description = "Crear una alarma de errores por Lambda (valor fijo: el ARN del tópico de alertas se conoce al aplicar)"
  type        = bool
  default     = true
}
variable "privacy_pepper_param_name" { type = string }

variable "bucket_via_cli" {
  description = "true en el Learner Lab: crea el bucket con la AWS CLI (ver modules/s3-bucket)"
  type        = bool
  default     = false
}


variable "federation_tournaments_enabled" {
  type    = bool
  default = true
}

data "aws_caller_identity" "current" {}

module "etl_bucket" {
  source  = "../s3-bucket"
  name    = "${var.name}-etl-${data.aws_caller_identity.current.account_id}"
  use_cli = var.bucket_via_cli
}

resource "aws_s3_bucket_public_access_block" "etl" {
  bucket                  = module.etl_bucket.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "etl" {
  bucket = module.etl_bucket.id
  rule {
    apply_server_side_encryption_by_default { sse_algorithm = "AES256" }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "etl" {
  bucket = module.etl_bucket.id
  rule {
    id     = "raw-30-dias"
    status = "Enabled"
    filter { prefix = "raw/" }
    expiration { days = 30 }
  }
}

# El zip debe llevar la carpeta del paquete (chessquery_etl/...) en su raíz: los handlers se importan como
# `chessquery_etl.handler` y el código usa imports relativos. Con `source_dir` el zip quedaba con los archivos
# sueltos y la Lambda fallaba con ImportModuleError. Solo se empaquetan los .py (el paquete no tiene otros archivos).
data "archive_file" "etl" {
  type        = "zip"
  output_path = "${path.root}/.build/chessquery-etl.zip"

  dynamic "source" {
    for_each = fileset(var.source_dir, "**/*.py")
    content {
      content  = file("${var.source_dir}/${source.value}")
      filename = "chessquery_etl/${source.value}"
    }
  }
}

locals {
  common_env = {
    ETL_BUCKET             = module.etl_bucket.id
    CHESS_EVENTS_TOPIC_ARN = var.topic_arn
    PRIVACY_PEPPER_PARAM   = var.privacy_pepper_param_name
  }
  functions = {
    fide-import = {
      handler = "chessquery_etl.handler.lambda_handler"
      timeout = 900
      memory  = 1024
      env     = {}
    }
    federation-tournaments = {
      handler = "chessquery_etl.federation.cli.lambda_handler"
      timeout = 300
      memory  = 256
      env     = { FEDERATION_TOURNAMENTS_ENABLED = tostring(var.federation_tournaments_enabled) }
    }
    federation-lookup = {
      handler = "chessquery_etl.federation.cli.lambda_handler"
      timeout = 60
      memory  = 256
      # La descarga masiva de jugadores queda apagada hasta que exista un convenio con la Federación
      env = { FEDERATION_BULK_PLAYERS_ENABLED = "false" }
    }
    external-ratings = {
      handler = "chessquery_etl.external.handler.lambda_handler"
      timeout = 300 # Chess.com va de a una cuenta con ritmo máximo: 100 cuentas ≈ 1 min
      memory  = 256
      env     = {}
    }
  }
  schedules = {
    fide-import            = { expression = "cron(0 9 2 * ? *)", input = jsonencode({}) }
    federation-tournaments = { expression = "cron(0 10 * * ? *)", input = jsonencode({ mode = "tournaments" }) }
  }
}

resource "aws_cloudwatch_log_group" "fn" {
  for_each          = local.functions
  name              = "/aws/lambda/${var.name}-${each.key}"
  retention_in_days = 14
}

resource "aws_lambda_function" "fn" {
  for_each         = local.functions
  function_name    = "${var.name}-${each.key}"
  role             = var.role_arn
  runtime          = "python3.12"
  handler          = each.value.handler
  filename         = data.archive_file.etl.output_path
  source_code_hash = data.archive_file.etl.output_base64sha256
  timeout          = each.value.timeout
  memory_size      = each.value.memory
  environment { variables = merge(local.common_env, each.value.env) }
  depends_on = [aws_cloudwatch_log_group.fn]
}

resource "aws_cloudwatch_event_rule" "schedule" {
  for_each            = local.schedules
  name                = "${var.name}-${each.key}"
  schedule_expression = each.value.expression
}

resource "aws_cloudwatch_event_target" "schedule" {
  for_each = local.schedules
  rule     = aws_cloudwatch_event_rule.schedule[each.key].name
  arn      = aws_lambda_function.fn[each.key].arn
  input    = each.value.input
}

resource "aws_lambda_permission" "schedule" {
  for_each      = local.schedules
  statement_id  = "AllowEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.fn[each.key].function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.schedule[each.key].arn
}

# Eventos del bus: SNS invoca la Lambda en forma asíncrona. Un pedido que falla se reintenta 2 veces (lo hace
# Lambda) y, si sigue fallando, queda registrado en el log y la alarma de errores avisa.
resource "aws_sns_topic_subscription" "fn" {
  for_each      = var.subscriptions
  topic_arn     = var.topic_arn
  protocol      = "lambda"
  endpoint      = aws_lambda_function.fn[each.key].arn
  filter_policy = jsonencode({ eventType = each.value })
}

resource "aws_lambda_permission" "sns" {
  for_each      = var.subscriptions
  statement_id  = "AllowChessEvents"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.fn[each.key].function_name
  principal     = "sns.amazonaws.com"
  source_arn    = var.topic_arn
}

resource "aws_lambda_function_event_invoke_config" "sns" {
  for_each                     = var.subscriptions
  function_name                = aws_lambda_function.fn[each.key].function_name
  maximum_retry_attempts       = 2
  maximum_event_age_in_seconds = 3600 # un pedido de ficha de hace más de 1 h ya no le sirve al jugador
}

resource "aws_cloudwatch_metric_alarm" "errors" {
  for_each            = var.error_alarms ? local.functions : {}
  alarm_name          = "${var.name}-${each.key}-errores"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  dimensions          = { FunctionName = aws_lambda_function.fn[each.key].function_name }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = compact([var.alarm_topic_arn])
}

output "bucket" { value = module.etl_bucket.id }
output "function_names" { value = { for k, f in aws_lambda_function.fn : k => f.function_name } }
