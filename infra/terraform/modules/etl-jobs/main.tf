# ETL en la nube: bucket de datos (raw/ se borra a los 30 días; staged/ y manifests/ quedan para el diff del mes
# siguiente) y tres Lambdas empaquetadas desde etl/chessquery_etl (sin dependencias extra: boto3 viene en el runtime).
#   - fide-import            mensual (día 2): lista FIDE (CHI) → rating.updated
#   - federation-tournaments diaria: torneos de la Federación → federation.tournament.published
#   - federation-lookup      por cola SQS: ficha de un jugador que la vinculó (consentimiento) → rating.updated
# Los horarios usan reglas de EventBridge (no Scheduler): no necesitan un rol propio, que el Learner Lab no deja crear.

variable "name" { type = string }
variable "role_arn" { type = string }
variable "source_dir" { type = string }
variable "topic_arn" { type = string }
variable "lookup_queue_arn" { type = string }
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

data "archive_file" "etl" {
  type        = "zip"
  source_dir  = var.source_dir
  output_path = "${path.root}/.build/chessquery-etl.zip"
  excludes    = ["**/__pycache__/**", "**/*.pyc"]
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

# Pedidos de consulta puntual: los que fallan vuelven a la cola (batchItemFailures) y, tras 5 intentos, a la DLQ.
resource "aws_lambda_event_source_mapping" "lookup" {
  event_source_arn        = var.lookup_queue_arn
  function_name           = aws_lambda_function.fn["federation-lookup"].arn
  batch_size              = 5
  function_response_types = ["ReportBatchItemFailures"]
}

output "bucket" { value = module.etl_bucket.id }
output "function_names" { value = { for k, f in aws_lambda_function.fn : k => f.function_name } }
