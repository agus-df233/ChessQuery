import json
from datetime import datetime, timezone

from chessquery_etl import pipeline
from chessquery_etl.fide import FidePlayer


class MemoryStorage:
    def __init__(self):
        self.objects: dict[str, str] = {}

    def get_text(self, key):
        return self.objects.get(key)

    def put_text(self, key, body):
        self.objects[key] = body


class MemoryBus:
    def __init__(self):
        self.sent: list[tuple[str, dict]] = []

    def publish(self, event_type, body):
        self.sent.append((event_type, json.loads(body)))


def player(i: int, standard: int | None = 1500, **kw) -> FidePlayer:
    base = dict(fide_id=str(9_100_000 + i), first_name="Nombre", last_name=f"Ficticio{i}", sex="M", title=None,
                birth_year=2000, standard=standard, rapid=None, blitz=None, inactive=False)
    base.update(kw)
    return FidePlayer(**base)


def test_first_run_publishes_all_rated_in_batches_with_the_java_envelope():
    storage, bus = MemoryStorage(), MemoryBus()
    players = [player(i) for i in range(450)] + [player(999, standard=None)]

    result = pipeline.run(players, "2026-10", storage, bus)

    assert result.as_dict() == {"period": "2026-10", "read": 451, "rated": 450, "changed": 450, "batches": 3}
    assert [len(body["payload"]["players"]) for _, body in bus.sent] == [200, 200, 50]
    event_type, body = bus.sent[0]
    assert event_type == body["eventType"] == "rating.updated"
    assert body["eventId"] and body["timestamp"].endswith("Z")
    assert body["payload"]["source"] == "FIDE" and body["payload"]["period"] == "2026-10"
    first = body["payload"]["players"][0]
    assert first["fideId"] == "9100000" and first["birthYear"] == 2000 and "eloFideRapid" not in first
    assert first["sourceUrl"] == "https://ratings.fide.com/profile/9100000"
    assert json.loads(storage.objects["manifests/source=fide/period=2026-10.json"])["changed"] == 450


def test_next_month_publishes_only_changes():
    storage, bus = MemoryStorage(), MemoryBus()
    pipeline.run([player(1), player(2)], "2026-10", storage, MemoryBus())

    result = pipeline.run([player(1), player(2, standard=1532), player(3)], "2026-11", storage, bus)

    assert result.changed == 2
    ids = [p["fideId"] for _, body in bus.sent for p in body["payload"]["players"]]
    assert ids == ["9100002", "9100003"]


def test_same_period_twice_is_idempotent_in_content():
    storage = MemoryStorage()
    pipeline.run([player(1)], "2026-12", storage, MemoryBus())
    # Enero compara contra diciembre del año anterior
    assert pipeline.previous_period("2027-01") == "2026-12"
    bus = MemoryBus()
    assert pipeline.run([player(1)], "2027-01", storage, bus).changed == 0
    assert bus.sent == []


def test_envelope_accepts_fixed_clock():
    body = json.loads(pipeline.envelope("x", {}, datetime(2026, 10, 2, 9, 0, tzinfo=timezone.utc)))
    assert body["timestamp"] == "2026-10-02T09:00:00Z"
