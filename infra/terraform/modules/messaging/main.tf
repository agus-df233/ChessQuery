# Bus de eventos (ADR-0002): tópico SNS chess-events → una cola SQS por consumidor con filter policy
# por el atributo eventType, raw delivery y DLQ. Espejo de infra/localstack/init/ready.d (local).

variable "name" { type = string }

variable "consumers" {
  description = "Cola → eventTypes que recibe (p. ej. users-elo = [\"elo.updated\"])"
  type        = map(list(string))
}

variable "max_receive_count" {
  type    = number
  default = 5
}

variable "alarm_topic_arn" {
  description = "Tópico SNS de alertas: se avisa cuando una DLQ recibe mensajes (null = sin alarma)"
  type        = string
  default     = null
}

resource "aws_sns_topic" "events" {
  name = "${var.name}-chess-events"
}

resource "aws_sqs_queue" "dlq" {
  for_each                  = var.consumers
  name                      = "${var.name}-${each.key}-dlq"
  message_retention_seconds = 1209600 # 14 días para inspeccionar y reprocesar
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "queue" {
  for_each                   = var.consumers
  name                       = "${var.name}-${each.key}"
  visibility_timeout_seconds = 60
  receive_wait_time_seconds  = 20 # long polling: menos requests (costo) y menos latencia
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = var.max_receive_count
  })
}

data "aws_iam_policy_document" "from_topic" {
  for_each = var.consumers
  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.queue[each.key].arn]
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.events.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "from_topic" {
  for_each  = var.consumers
  queue_url = aws_sqs_queue.queue[each.key].id
  policy    = data.aws_iam_policy_document.from_topic[each.key].json
}

resource "aws_sns_topic_subscription" "queue" {
  for_each             = var.consumers
  topic_arn            = aws_sns_topic.events.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.queue[each.key].arn
  raw_message_delivery = true
  filter_policy        = jsonencode({ eventType = each.value })
}

resource "aws_cloudwatch_metric_alarm" "dlq" {
  for_each            = var.alarm_topic_arn == null ? {} : var.consumers
  alarm_name          = "${var.name}-${each.key}-dlq"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.dlq[each.key].name }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [var.alarm_topic_arn]
}

output "topic_arn" { value = aws_sns_topic.events.arn }
output "queue_names" { value = { for k, q in aws_sqs_queue.queue : k => q.name } }
output "queue_arns" { value = [for q in aws_sqs_queue.queue : q.arn] }
