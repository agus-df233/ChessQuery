"""Consultas puntuales pedidas por los jugadores (evento ``federation.lookup.requested``).

Cuando un jugador vincula su id federativo en ChessQuery, ``users`` publica ese evento en el tópico ``chess-events``
y SNS se lo entrega **directo** a la Lambda ``federation-lookup`` (suscripción con filtro por ``eventType``, sin cola
SQS de por medio). Acá se interpreta cada entrega y se corre el modo ``lookup``, que termina publicando
``rating.updated`` con la ficha del jugador.

- En la nube: ``lambda_handler`` de ``cli.py`` recibe ``Records`` con formato SNS.
- En local: el receptor ``chessquery_etl.local_bus`` se suscribe al tópico de LocalStack y arma el mismo evento.
"""
from __future__ import annotations

import json
import logging
from typing import Callable, Iterable

EVENT_LOOKUP_REQUESTED = "federation.lookup.requested"

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


def message_of(record: dict) -> str:
    """Cuerpo del ChessEvent dentro de un registro SNS (formato del evento de Lambda)."""
    return (record.get("Sns") or {}).get("Message", "")


def handle_records(records: Iterable[dict], lookup: Lookup) -> dict:
    """Procesa las entregas de SNS. Si una consulta falla, la excepción sube: Lambda reintenta la invocación
    (2 veces) y la alarma de errores avisa. Los mensajes inválidos se descartan con un log, porque reintentarlos
    no los arregla."""
    done = 0
    for record in records:
        fid = federation_id_of(message_of(record))
        if fid is None:
            log.warning("Mensaje descartado (no es un pedido de consulta válido): %s",
                        (record.get("Sns") or {}).get("MessageId"))
            continue
        lookup(fid)
        done += 1
    return {"lookups": done}
