"""Esqueleto común de toda ingesta: staged → diff → publicación en lotes → manifest.

Rol en el flujo (ver docs/etl/onboarding.md): cada fuente (FIDE, Federación) entrega filas ya
validadas y minimizadas; este módulo las guarda en S3 (``staged/``), compara contra la corrida
anterior para publicar solo lo que cambió (idempotencia) y deja un ``manifest`` con los conteos.
Los eventos usan el mismo envelope que ``cl.chessquery.common.events.ChessEvent`` (docs/events.md).
"""
from __future__ import annotations

import json
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable, Iterable, Protocol

from .fide import FidePlayer

EVENT_RATING_UPDATED = "rating.updated"
BATCH_SIZE = 200  # 200 jugadores ≈ 60 KB: holgado bajo el límite de 256 KB de un mensaje SNS
FIDE_SOURCE = "FIDE"
FIDE_PROFILE_URL = "https://ratings.fide.com/profile/{}"


class Storage(Protocol):
    def get_text(self, key: str) -> str | None: ...
    def put_text(self, key: str, body: str) -> None: ...


class Bus(Protocol):
    def publish(self, event_type: str, body: str) -> None: ...


@dataclass
class Result:
    period: str
    read: int
    rated: int
    changed: int
    batches: int

    def as_dict(self) -> dict:
        return self.__dict__.copy()


# ── Piezas reutilizables por cualquier fuente ────────────────────────────────

def envelope(event_type: str, payload: dict, now: datetime | None = None) -> str:
    """Serializa un evento con el contrato de ChessEvent: eventId, eventType, timestamp y payload."""
    ts = (now or datetime.now(timezone.utc)).isoformat().replace("+00:00", "Z")
    return json.dumps({"eventId": str(uuid.uuid4()), "eventType": event_type, "timestamp": ts,
                       "payload": payload}, ensure_ascii=False)


def load_jsonl(storage: Storage, key: str, id_field: str) -> dict[str, dict]:
    """Lee una corrida anterior guardada en S3 como {id: fila}. Si no hay corrida previa, devuelve {}."""
    text = storage.get_text(key)
    if not text:
        return {}
    rows = (json.loads(line) for line in text.splitlines() if line.strip())
    return {r[id_field]: r for r in rows}


def stage(storage: Storage, key: str, rows: list[dict]) -> None:
    """Guarda las filas de esta corrida (JSON Lines) para que la próxima pueda compararse."""
    storage.put_text(key, "\n".join(json.dumps(r, ensure_ascii=False) for r in rows))


def changed_rows(rows: list[dict], previous: dict[str, dict], id_field: str) -> list[dict]:
    """Filas nuevas o distintas a la corrida anterior: es lo único que vale la pena publicar."""
    return [r for r in rows if previous.get(r[id_field]) != r]


def publish_batches(bus: Bus, event_type: str, items: list, build_payload: Callable[[list], dict]) -> int:
    """Publica ``items`` en lotes de BATCH_SIZE y devuelve cuántos lotes se enviaron."""
    batches = 0
    for i in range(0, len(items), BATCH_SIZE):
        bus.publish(event_type, envelope(event_type, build_payload(items[i:i + BATCH_SIZE])))
        batches += 1
    return batches


def write_manifest(storage: Storage, key: str, summary: dict) -> None:
    """Deja el resumen de la corrida (conteos) para auditoría y alarmas."""
    storage.put_text(key, json.dumps(summary, ensure_ascii=False))


def previous_period(period: str) -> str:
    """Período anterior en formato AAAA-MM (enero compara contra diciembre del año pasado)."""
    year, month = map(int, period.split("-"))
    return f"{year - 1}-12" if month == 1 else f"{year}-{month - 1:02d}"


# ── Fuente FIDE ──────────────────────────────────────────────────────────────

def staged_key(period: str) -> str:
    return f"staged/source=fide/period={period}/players.jsonl"


def to_event_player(p: FidePlayer, period: str) -> dict:
    """Forma que entiende RatingUpdatedConsumer (camelCase, sin nulos)."""
    out = {
        "firstName": p.first_name,
        "lastName": p.last_name,
        "fideId": p.fide_id,
        "title": p.title,
        "birthYear": p.birth_year,
        "eloFideStandard": p.standard,
        "eloFideRapid": p.rapid,
        "eloFideBlitz": p.blitz,
        "sourceUrl": FIDE_PROFILE_URL.format(p.fide_id),
        "period": period,
    }
    return {k: v for k, v in out.items() if v is not None}


def run(players: Iterable[FidePlayer], period: str, storage: Storage, bus: Bus) -> Result:
    """Corrida mensual FIDE: toma solo jugadores con rating y publica los que cambiaron desde el mes anterior."""
    read = list(players)
    rated = [p.as_dict() for p in read if p.has_rating()]
    previous = load_jsonl(storage, staged_key(previous_period(period)), "fide_id")
    changed = changed_rows(rated, previous, "fide_id")

    stage(storage, staged_key(period), rated)
    batches = publish_batches(bus, EVENT_RATING_UPDATED, changed, lambda chunk: {
        "source": FIDE_SOURCE, "period": period,
        "players": [to_event_player(FidePlayer(**row), period) for row in chunk]})

    result = Result(period=period, read=len(read), rated=len(rated), changed=len(changed), batches=batches)
    write_manifest(storage, f"manifests/source=fide/period={period}.json", result.as_dict())
    return result
