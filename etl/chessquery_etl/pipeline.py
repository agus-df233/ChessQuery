"""Pipeline mensual FIDE → S3 (raw/staged/manifest) → SNS ``rating.updated``.

Contrato del evento: docs/events.md. Solo se publican los jugadores con rating que
cambiaron respecto del período anterior (diff), en lotes de ``BATCH_SIZE`` para no pasar
el límite de 256 KB de SNS. La lista de supresión la aplica ``users`` al consumir (es el
dueño del dato): así el ETL no necesita acceso a la base ni a la VPC.
"""
from __future__ import annotations

import json
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Iterable, Protocol

from .fide import FidePlayer

SOURCE = "FIDE"
EVENT_TYPE = "rating.updated"
BATCH_SIZE = 200
PROFILE_URL = "https://ratings.fide.com/profile/{}"


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


def previous_period(period: str) -> str:
    year, month = map(int, period.split("-"))
    return f"{year - 1}-12" if month == 1 else f"{year}-{month - 1:02d}"


def staged_key(period: str) -> str:
    return f"staged/source=fide/period={period}/players.jsonl"


def _load_previous(storage: Storage, period: str) -> dict[str, dict]:
    text = storage.get_text(staged_key(previous_period(period)))
    if not text:
        return {}
    rows = (json.loads(line) for line in text.splitlines() if line.strip())
    return {r["fide_id"]: r for r in rows}


def to_event_player(p: FidePlayer, period: str) -> dict:
    """Forma que entiende RatingUpdatedConsumer (camelCase, sin nulos)."""
    out = {
        "firstName": p.first_name,
        "lastName": p.last_name,
        "fideId": p.fide_id,
        "birthYear": p.birth_year,
        "eloFideStandard": p.standard,
        "eloFideRapid": p.rapid,
        "eloFideBlitz": p.blitz,
        "sourceUrl": PROFILE_URL.format(p.fide_id),
        "period": period,
    }
    return {k: v for k, v in out.items() if v is not None}


def envelope(event_type: str, payload: dict, now: datetime | None = None) -> str:
    """Mismo envelope que cl.chessquery.common.events.ChessEvent."""
    ts = (now or datetime.now(timezone.utc)).isoformat().replace("+00:00", "Z")
    return json.dumps({"eventId": str(uuid.uuid4()), "eventType": event_type, "timestamp": ts,
                       "payload": payload}, ensure_ascii=False)


def run(players: Iterable[FidePlayer], period: str, storage: Storage, bus: Bus) -> Result:
    read = list(players)
    rated = [p for p in read if p.has_rating()]
    previous = _load_previous(storage, period)
    changed = [p for p in rated if previous.get(p.fide_id) != p.as_dict()]

    storage.put_text(staged_key(period), "\n".join(json.dumps(p.as_dict(), ensure_ascii=False) for p in rated))

    batches = 0
    for i in range(0, len(changed), BATCH_SIZE):
        chunk = changed[i:i + BATCH_SIZE]
        payload = {"source": SOURCE, "period": period, "players": [to_event_player(p, period) for p in chunk]}
        bus.publish(EVENT_TYPE, envelope(EVENT_TYPE, payload))
        batches += 1

    result = Result(period=period, read=len(read), rated=len(rated), changed=len(changed), batches=batches)
    storage.put_text(f"manifests/source=fide/period={period}.json", json.dumps(result.as_dict()))
    return result
