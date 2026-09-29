"""Consultas puntuales pedidas por los jugadores (evento ``federation.lookup.requested``).

Cuando un jugador vincula su id federativo en ChessQuery, ``users`` publica ese evento y la cola
``etl-federation-lookup`` lo recibe. Acá se lee la cola y, por cada pedido, se corre el modo ``lookup``
(que termina publicando ``rating.updated`` con la ficha del jugador).

- En la nube: la cola dispara la Lambda (``lambda_handler`` de ``cli.py`` recibe ``Records`` de SQS).
- En local: ``python -m chessquery_etl.federation.cli worker`` hace *long polling* sobre la cola de LocalStack.
"""
from __future__ import annotations

import json
import logging
from typing import Callable, Iterable

EVENT_LOOKUP_REQUESTED = "federation.lookup.requested"
DEFAULT_QUEUE = "etl-federation-lookup"

log = logging.getLogger(__name__)

Lookup = Callable[[str], dict]


def federation_id_of(body: str) -> str | None:
    """Saca el id federativo del sobre ChessEvent. Devuelve None si el mensaje no es un pedido válido."""
    try:
        event = json.loads(body)
    except json.JSONDecodeError:
        return None
    if not isinstance(event, dict) or event.get("eventType") != EVENT_LOOKUP_REQUESTED:
        return None
    fid = str((event.get("payload") or {}).get("federationId") or "").strip()
    return fid if fid.isdigit() else None


def handle_records(records: Iterable[dict], lookup: Lookup) -> dict:
    """Procesa mensajes SQS (formato Lambda). Los que fallan se devuelven en ``batchItemFailures`` para
    reintento (y luego DLQ); los inválidos se descartan con un log, porque reintentarlos no los arregla."""
    failures, done = [], 0
    for record in records:
        fid = federation_id_of(record.get("body", ""))
        if fid is None:
            log.warning("Mensaje descartado (no es un pedido de consulta válido): %s", record.get("messageId"))
            continue
        try:
            lookup(fid)
            done += 1
        except Exception:  # noqa: BLE001 — cualquier error se reintenta vía SQS
            log.exception("Falló la consulta puntual de la ficha %s", fid)
            failures.append({"itemIdentifier": record.get("messageId")})
    return {"lookups": done, "batchItemFailures": failures}


def poll(sqs, queue_url: str, lookup: Lookup, *, once: bool = False) -> int:
    """Long polling local: procesa y borra los mensajes exitosos. Con ``once`` hace una sola vuelta (tests)."""
    total = 0
    while True:
        resp = sqs.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=10, WaitTimeSeconds=20)
        records = [{"messageId": m["MessageId"], "body": m["Body"], "receipt": m["ReceiptHandle"]}
                   for m in resp.get("Messages", [])]
        result = handle_records(records, lookup)
        failed = {f["itemIdentifier"] for f in result["batchItemFailures"]}
        for r in records:
            if r["messageId"] not in failed:
                sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=r["receipt"])
        total += result["lookups"]
        if once:
            return total
