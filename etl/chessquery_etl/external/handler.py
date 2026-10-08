"""Lambda ``external-ratings``: ratings públicos de Lichess y Chess.com de las cuentas vinculadas.

``users`` publica ``external.ratings.sync.requested`` (al vincular una cuenta, al apretar «Sincronizar» y una vez al
día) y SNS se lo entrega directo a esta Lambda. Por cada lote de cuentas se leen los ratings y se publica
``rating.updated`` (source ``LICHESS`` / ``CHESSCOM``), que ``users`` aplica con ``LinkedAccountRatingSource`` (solo a
quien vinculó ese username: un username no es una identidad).

En local lo atiende el receptor SNS (``chessquery_etl.local_bus``) con el mismo handler.
"""
from __future__ import annotations

import json
import logging
import os

from .. import handler as fide_handler
from ..pipeline import publish_batches
from . import sources
from .http import PoliteHttp

EVENT_SYNC_REQUESTED = "external.ratings.sync.requested"
RATING_UPDATED = "rating.updated"

log = logging.getLogger(__name__)


def accounts_of(records: list[dict]) -> tuple[list[str], list[str]]:
    """Usernames válidos (sin repetir) de los pedidos que trae SNS; lo que no es un pedido se ignora con un log."""
    lichess, chesscom = set(), set()
    for record in records:
        for account in _accounts(record):
            if sources.valid(account.get("lichessUsername")):
                lichess.add(account["lichessUsername"])
            if sources.valid(account.get("chesscomUsername")):
                chesscom.add(account["chesscomUsername"])
    return sorted(lichess), sorted(chesscom)


def _accounts(record: dict) -> list[dict]:
    """Cuentas de un registro SNS; vacío si el mensaje no es un pedido de ratings externos."""
    try:
        event = json.loads((record.get("Sns") or {}).get("Message", ""))
    except json.JSONDecodeError:
        event = None
    if not isinstance(event, dict) or event.get("eventType") != EVENT_SYNC_REQUESTED:
        log.warning("Mensaje descartado (no es un pedido de ratings externos)")
        return []
    return [a for a in (event.get("payload") or {}).get("accounts") or [] if isinstance(a, dict)]


def run(lichess_users: list[str], chesscom_users: list[str], bus, *, lichess_base: str, chesscom_base: str,
        lichess_http: PoliteHttp | None = None, chesscom_http: PoliteHttp | None = None) -> dict:
    """Lee los ratings y publica ``rating.updated`` por fuente. Devuelve el resumen de la corrida."""
    lichess_http = lichess_http or PoliteHttp(min_interval=1.0)
    chesscom_http = chesscom_http or PoliteHttp(min_interval=0.5)  # Chess.com: en serie, nunca en paralelo
    from_lichess = sources.lichess(lichess_http, lichess_base, lichess_users) if lichess_users else []
    from_chesscom = [row for u in chesscom_users if (row := sources.chesscom(chesscom_http, chesscom_base, u))]
    for source, players in (("LICHESS", from_lichess), ("CHESSCOM", from_chesscom)):
        if players:
            publish_batches(bus, RATING_UPDATED, players, lambda batch, s=source: {"source": s, "players": batch})
    return {"lichess": len(from_lichess), "chesscom": len(from_chesscom),
            "requested": {"lichess": len(lichess_users), "chesscom": len(chesscom_users)}}


def lambda_handler(event, _context, env=os.environ, bus=None):
    lichess_users, chesscom_users = accounts_of((event or {}).get("Records") or [])
    if bus is None:
        _, sns = fide_handler._clients()
        bus = fide_handler.SnsBus(sns, env.get("CHESS_EVENTS_TOPIC_ARN", "arn:aws:sns:us-east-1:000000000000:chess-events"))
    summary = run(lichess_users, chesscom_users, bus,
                  lichess_base=env.get("LICHESS_API_BASE", "https://lichess.org"),
                  chesscom_base=env.get("CHESSCOM_API_BASE", "https://api.chess.com"))
    log.info("Ratings externos: %s", summary)
    return summary
