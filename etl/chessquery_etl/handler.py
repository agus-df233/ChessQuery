"""Entrada de la Lambda ``fide_import`` y CLI local.

Lambda (EventBridge Scheduler, día 2 de cada mes): descarga la lista oficial, guarda el zip en
``raw/`` (lifecycle de 30 días en el bucket) y corre el pipeline.

Local contra LocalStack (``make etl-fide-local``)::

    python -m chessquery_etl.handler --file tests/fixtures/players_list_sample.txt --period 2026-10

boto3 toma ``AWS_ENDPOINT_URL`` del entorno, así que el mismo código sirve en LocalStack y en AWS.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import urllib.request
import zipfile
from datetime import datetime, timezone

from . import fide, pipeline

DEFAULT_URL = "https://ratings.fide.com/download/players_list.zip"
USER_AGENT = "ChessQuery-ETL/3 (+https://chessquery.cl)"


class S3Storage:
    def __init__(self, client, bucket: str):
        self.client, self.bucket = client, bucket

    def get_text(self, key: str) -> str | None:
        try:
            return self.client.get_object(Bucket=self.bucket, Key=key)["Body"].read().decode("utf-8")
        except self.client.exceptions.NoSuchKey:
            return None

    def put_text(self, key: str, body: str) -> None:
        self.client.put_object(Bucket=self.bucket, Key=key, Body=body.encode("utf-8"))

    def put_bytes(self, key: str, body: bytes) -> None:
        self.client.put_object(Bucket=self.bucket, Key=key, Body=body)


class SnsBus:
    def __init__(self, client, topic_arn: str):
        self.client, self.topic_arn = client, topic_arn

    def publish(self, event_type: str, body: str) -> None:
        self.client.publish(TopicArn=self.topic_arn, Message=body, MessageAttributes={
            "eventType": {"DataType": "String", "StringValue": event_type}})


def current_period(now: datetime | None = None) -> str:
    return (now or datetime.now(timezone.utc)).strftime("%Y-%m")


def lines_from_zip(data: bytes) -> list[str]:
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        name = next(n for n in z.namelist() if n.endswith(".txt"))
        return z.read(name).decode("latin-1").splitlines()


def download(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as resp:  # noqa: S310 (URL fija de FIDE o de config)
        return resp.read()


def _clients():
    import boto3  # import tardío: los tests del parser no necesitan boto3
    return boto3.client("s3"), boto3.client("sns")


def import_lines(lines: list[str], period: str, bucket: str, topic_arn: str, clients=None) -> dict:
    s3, sns = clients or _clients()
    result = pipeline.run(fide.parse(lines), period, S3Storage(s3, bucket), SnsBus(sns, topic_arn))
    return result.as_dict()


def lambda_handler(event, _context):
    period = (event or {}).get("period") or current_period()
    data = download(os.environ.get("FIDE_LIST_URL", DEFAULT_URL))
    s3, sns = _clients()
    bucket = os.environ["ETL_BUCKET"]
    S3Storage(s3, bucket).put_bytes(f"raw/source=fide/period={period}/players_list.zip", data)
    return import_lines(lines_from_zip(data), period, bucket, os.environ["CHESS_EVENTS_TOPIC_ARN"], (s3, sns))


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description="Importa la lista FIDE (CHI) y publica rating.updated")
    ap.add_argument("--file", help="TXT o ZIP local; sin esto descarga la lista oficial")
    ap.add_argument("--period", default=current_period())
    ap.add_argument("--bucket", default=os.environ.get("ETL_BUCKET", "chessquery-etl"))
    ap.add_argument("--topic-arn", default=os.environ.get(
        "CHESS_EVENTS_TOPIC_ARN", "arn:aws:sns:us-east-1:000000000000:chess-events"))
    args = ap.parse_args(argv)

    if args.file:
        with open(args.file, "rb") as fh:
            raw = fh.read()
        lines = lines_from_zip(raw) if args.file.endswith(".zip") else raw.decode("latin-1").splitlines()
    else:
        lines = lines_from_zip(download(os.environ.get("FIDE_LIST_URL", DEFAULT_URL)))
    print(json.dumps(import_lines(lines, args.period, args.bucket, args.topic_arn), indent=2))


if __name__ == "__main__":  # pragma: no cover
    main()
