# Apagado nocturno automático (cuida el crédito del Learner Lab): una Lambda con el LabRole, disparada por una regla
# de EventBridge, deja los servicios de ECS en 0 y detiene RDS. Usa EventBridge y no Scheduler porque Scheduler
# necesita un rol propio y el lab no deja crear roles. No borra nada; se vuelve a encender con `make academy-up`.
# RDS detenida se reinicia sola a los 7 días (regla de AWS): la Lambda la vuelve a apagar esa misma noche.

variable "name" { type = string }
variable "role_arn" { type = string }
variable "cluster_name" { type = string }
variable "db_identifier" { type = string }

variable "schedule" {
  description = "Cuándo apagar (UTC). Por defecto 02:00 UTC = 23:00 en Chile con horario de verano (UTC-3)"
  type        = string
  default     = "cron(0 2 * * ? *)"
}

data "archive_file" "fn" {
  type        = "zip"
  source_file = "${path.module}/apagado.py"
  output_path = "${path.root}/.build/apagado-nocturno.zip"
}

resource "aws_cloudwatch_log_group" "fn" {
  name              = "/aws/lambda/${var.name}-apagado-nocturno"
  retention_in_days = 14
}

resource "aws_lambda_function" "fn" {
  function_name    = "${var.name}-apagado-nocturno"
  role             = var.role_arn
  runtime          = "python3.12"
  handler          = "apagado.lambda_handler"
  filename         = data.archive_file.fn.output_path
  source_code_hash = data.archive_file.fn.output_base64sha256
  timeout          = 60
  memory_size      = 128
  environment {
    variables = { CLUSTER = var.cluster_name, DB_IDENTIFIER = var.db_identifier }
  }
  depends_on = [aws_cloudwatch_log_group.fn]
}

resource "aws_cloudwatch_event_rule" "nightly" {
  name                = "${var.name}-apagado-nocturno"
  schedule_expression = var.schedule
}

resource "aws_cloudwatch_event_target" "nightly" {
  rule = aws_cloudwatch_event_rule.nightly.name
  arn  = aws_lambda_function.fn.arn
}

resource "aws_lambda_permission" "nightly" {
  statement_id  = "AllowEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.fn.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.nightly.arn
}

output "function_name" { value = aws_lambda_function.fn.function_name }
