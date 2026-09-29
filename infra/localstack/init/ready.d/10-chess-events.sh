#!/bin/bash
# Topología local del bus y buckets (ADR-0002). Espejo de infra/terraform/modules/messaging:
# tópico SNS chess-events → una cola SQS por consumidor con filter policy por eventType, raw
# delivery y DLQ (5 intentos). Las colas salen de infra/events/topology.json (la misma fuente que Terraform).
set -euo pipefail

TOPIC_ARN=$(awslocal sns create-topic --name chess-events --query TopicArn --output text)

TOPOLOGY=/etc/chessquery/events/topology.json

# Una línea "cola|eventType[,eventType...]" por consumidor
mapfile -t CONSUMERS < <(python3 -c '
import json, sys
for queue, types in json.load(open(sys.argv[1]))["consumers"].items():
    print(queue + "|" + ",".join(types))' "$TOPOLOGY")

for entry in "${CONSUMERS[@]}"; do
  queue="${entry%%|*}"
  types="${entry#*|}"
  dlq_url=$(awslocal sqs create-queue --queue-name "${queue}-dlq" --query QueueUrl --output text)
  dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn --query Attributes.QueueArn --output text)
  url=$(awslocal sqs create-queue --queue-name "$queue" \
    --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${dlq_arn}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}" \
    --query QueueUrl --output text)
  arn=$(awslocal sqs get-queue-attributes --queue-url "$url" --attribute-names QueueArn --query Attributes.QueueArn --output text)
  filter=$(printf '"%s",' ${types//,/ }); filter="{\"eventType\":[${filter%,}]}"
  awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$arn" \
    --attributes "{\"RawMessageDelivery\":\"true\",\"FilterPolicy\":$(printf '%s' "$filter" | sed 's/"/\\"/g; s/^/"/; s/$/"/')}" >/dev/null
  echo "chess-events → ${queue} (${types}) + ${queue}-dlq"
done

# Buckets: archivos de la app (PGN, logos) y datos del ETL (raw/, staged/).
awslocal s3 mb s3://chessquery-files >/dev/null
awslocal s3 mb s3://chessquery-etl >/dev/null
echo "buckets: chessquery-files, chessquery-etl"
