"""Entrada de la ingesta de la Federación: CLI local y handler de Lambda.

Comandos (``python -m chessquery_etl.federation.cli <comando>``):

- ``contract``      verifica el esquema de la Federación (no baja datos de personas).
- ``tournaments``   trae los torneos rankeados (o ``--word``) y publica los nuevos/modificados.
- ``lookup ID``     consulta puntual de un jugador por id federativo (vía consentida).
- ``players-bulk``  descarga masiva; falla si ``FEDERATION_BULK_PLAYERS_ENABLED`` no está en true.

Lambda: el evento indica el modo, p. ej. ``{"mode": "tournaments"}`` o ``{"mode": "lookup", "federationId": "738"}``.
Si el evento trae ``Records`` son pedidos de jugadores que SNS entrega directo (ver ``worker.py``). En local los
atiende el receptor ``python -m chessquery_etl.local_bus`` (``make etl-bus-local``).
Variables: ver docs/etl/federacion.md. S3/SNS se resuelven igual que en FIDE (``AWS_ENDPOINT_URL`` en local).
"""
from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone

from .. import handler as fide_handler
from . import contract, ingest, players, tournaments, worker
from .client import FederationClient
from .config import FederationConfig
from .privacy import Pepper


def _today() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d")


def _sinks(env) -> tuple:
    s3, sns = fide_handler._clients()
    bucket = env.get("ETL_BUCKET", "chessquery-etl")
    topic = env.get("CHESS_EVENTS_TOPIC_ARN", "arn:aws:sns:us-east-1:000000000000:chess-events")
    return fide_handler.S3Storage(s3, bucket), fide_handler.SnsBus(sns, topic)


def execute(mode: str, env=os.environ, *, federation_id: str | None = None, word: str | None = None,
            client: FederationClient | None = None, sinks: tuple | None = None) -> dict:
    """Corre un modo y devuelve el resumen. ``client`` y ``sinks`` se inyectan en los tests."""
    config = FederationConfig.from_env(env)
    client = client or FederationClient(config)
    if mode == "contract":
        return {"contract": "ok", "fields_by_type": contract.check_contract(client)}
    contract.check_contract(client)  # nunca se bajan datos si el esquema cambió
    storage, bus = sinks or _sinks(env)
    if mode == "tournaments":
        if not config.tournaments_enabled:
            return {"skipped": "FEDERATION_TOURNAMENTS_ENABLED=false"}
        raw = tournaments.fetch(client, word)
        return ingest.run_tournaments(raw, run_date=_today(), config=config, storage=storage, bus=bus)
    people = players.lookup(client, federation_id) if mode == "lookup" else players.bulk(client, config)
    return ingest.run_players(people, mode="lookup" if mode == "lookup" else "bulk",
                              period=fide_handler.current_period(), pepper=Pepper.from_env(env),
                              config=config, storage=storage, bus=bus)


def lambda_handler(event, _context):
    event = event or {}
    if "Records" in event:
        return worker.handle_records(event["Records"], lambda fid: execute("lookup", federation_id=fid))
    return execute(event.get("mode", "tournaments"), federation_id=event.get("federationId"), word=event.get("word"))



def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description="Ingesta de la Federación Chilena de Ajedrez")
    sub = ap.add_subparsers(dest="mode", required=True)
    sub.add_parser("contract")
    sub.add_parser("tournaments").add_argument("--word")
    sub.add_parser("lookup").add_argument("federation_id")
    sub.add_parser("players-bulk")
    args = ap.parse_args(argv)
    mode = "bulk" if args.mode == "players-bulk" else args.mode
    print(json.dumps(execute(mode, federation_id=getattr(args, "federation_id", None),
                             word=getattr(args, "word", None)), indent=2, ensure_ascii=False))


if __name__ == "__main__":  # pragma: no cover
    main()
