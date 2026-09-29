import json

from chessquery_etl.federation import cli, worker


def sobre(fid, event_type=worker.EVENT_LOOKUP_REQUESTED):
    return json.dumps({"eventId": "e1", "eventType": event_type, "timestamp": "2026-10-01T00:00:00Z",
                       "payload": {"playerId": 7, "federationId": fid}})


def test_extrae_el_id_solo_de_pedidos_validos():
    assert worker.federation_id_of(sobre("738")) == "738"
    assert worker.federation_id_of(sobre(738)) == "738"
    assert worker.federation_id_of(sobre("abc")) is None
    assert worker.federation_id_of(sobre("738", "rating.updated")) is None
    assert worker.federation_id_of("no es json") is None
    assert worker.federation_id_of("[]") is None


def test_lambda_sqs_reintenta_solo_los_que_fallan():
    vistos = []

    def lookup(fid):
        vistos.append(fid)
        if fid == "2":
            raise RuntimeError("Federación caída")
        return {}

    records = [{"messageId": "a", "body": sobre("1")}, {"messageId": "b", "body": sobre("2")},
               {"messageId": "c", "body": "basura"}]
    assert worker.handle_records(records, lookup) == {"lookups": 1, "batchItemFailures": [{"itemIdentifier": "b"}]}
    assert vistos == ["1", "2"]


def test_lambda_handler_detecta_eventos_de_sqs(monkeypatch):
    llamados = []
    monkeypatch.setattr(cli, "execute", lambda mode, **kw: llamados.append((mode, kw["federation_id"])) or {})
    out = cli.lambda_handler({"Records": [{"messageId": "a", "body": sobre("738")}]}, None)
    assert out == {"lookups": 1, "batchItemFailures": []}
    assert llamados == [("lookup", "738")]


class FakeSqs:
    def __init__(self, bodies):
        self.messages = [{"MessageId": str(i), "Body": b, "ReceiptHandle": f"r{i}"} for i, b in enumerate(bodies)]
        self.deleted = []

    def receive_message(self, **_):
        return {"Messages": self.messages}

    def delete_message(self, QueueUrl, ReceiptHandle):
        self.deleted.append(ReceiptHandle)


def test_worker_local_borra_lo_procesado_y_deja_lo_fallido():
    sqs = FakeSqs([sobre("1"), sobre("2"), "basura"])

    def lookup(fid):
        if fid == "2":
            raise RuntimeError("timeout")

    assert worker.poll(sqs, "url", lookup, once=True) == 1
    assert sqs.deleted == ["r0", "r2"]  # el fallido queda en la cola para reintento
