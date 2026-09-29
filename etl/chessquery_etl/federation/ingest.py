"""Orquesta una corrida de la Federación: validar → minimizar → staged → diff → publicar → manifest.

Usa las piezas comunes de ``pipeline.py`` (las mismas que FIDE). Lo específico de esta fuente es la
validación (``validation.py``), la minimización (``players.to_event_player``) y el umbral de rechazos:
si se supera, se guardan los rechazos y la corrida termina sin publicar nada.
"""
from __future__ import annotations

import json

from .. import pipeline
from ..pipeline import Bus, Storage
from . import players, tournaments
from .config import FederationConfig
from .privacy import Pepper
from .validation import PERSON_RULES, TOURNAMENT_RULES, Report, run_rules


def _save_rejected(storage: Storage, key: str, report: Report) -> None:
    if report.rejected:
        storage.put_text(key, "\n".join(json.dumps(r, ensure_ascii=False) for r in report.rejected))


def _summary(report: Report, changed: int, batches: int, **extra) -> dict:
    return {**extra, "read": report.total, "accepted": len(report.accepted), "rejected": len(report.rejected),
            "changed": changed, "batches": batches}


def run_players(people: list[dict], *, mode: str, period: str, pepper: Pepper, config: FederationConfig,
                storage: Storage, bus: Bus) -> dict:
    """Jugadores (``mode`` = lookup | bulk). El diff solo aplica al masivo; una consulta puntual siempre publica."""
    prefix = f"source=federation-players/mode={mode}/period={period}"
    report = run_rules(people, PERSON_RULES)
    _save_rejected(storage, f"rejected/{prefix}.jsonl", report)
    report.ensure_below(config.max_rejected_ratio)

    rows = [players.to_event_player(p, pepper, period) for p in report.accepted]
    staged = f"staged/{prefix}/players.jsonl"
    previous = pipeline.load_jsonl(storage, staged, "federationId") if mode == "bulk" else {}
    changed = pipeline.changed_rows(rows, previous, "federationId")
    pipeline.stage(storage, staged, rows)
    batches = pipeline.publish_batches(bus, pipeline.EVENT_RATING_UPDATED, changed, lambda chunk: {
        "source": players.SOURCE, "period": period, "players": chunk})

    summary = _summary(report, len(changed), batches, mode=mode, period=period)
    pipeline.write_manifest(storage, f"manifests/{prefix}.json", summary)
    return summary


def run_tournaments(raw: list[dict], *, run_date: str, config: FederationConfig, storage: Storage, bus: Bus) -> dict:
    """Torneos: publica solo los nuevos o modificados respecto de la última corrida (``latest``)."""
    report = run_rules(raw, TOURNAMENT_RULES)
    _save_rejected(storage, f"rejected/source=federation-tournaments/date={run_date}.jsonl", report)
    report.ensure_below(config.max_rejected_ratio)

    rows = [tournaments.to_event_tournament(t) for t in report.accepted]
    latest = "staged/source=federation-tournaments/latest.jsonl"
    changed = pipeline.changed_rows(rows, pipeline.load_jsonl(storage, latest, "federationTournamentId"),
                                    "federationTournamentId")
    pipeline.stage(storage, latest, rows)
    pipeline.stage(storage, f"staged/source=federation-tournaments/date={run_date}.jsonl", rows)
    batches = pipeline.publish_batches(bus, tournaments.EVENT_TYPE, changed, lambda chunk: {
        "source": players.SOURCE, "tournaments": chunk})

    summary = _summary(report, len(changed), batches, date=run_date)
    pipeline.write_manifest(storage, f"manifests/source=federation-tournaments/date={run_date}.json", summary)
    return summary
