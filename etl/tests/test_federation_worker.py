import json

import pytest

from chessquery_etl.federation import cli, worker


def sobre(fid, event_type=worker.EVENT_LOOKUP_REQUESTED):
    return json.dumps({"eventId": "e1", "eventType": event_type, "timestamp": "2026-10-01T00:00:00Z",
                       "payload": {"playerId": 7, "federationId": fid}})


def registro(body, message_id="m1"):
    """Registro tal como SNS se lo entrega a la Lambda."""
    return {"EventSource": "aws:sns", "Sns": {"MessageId": message_id, "Message": body}}


def test_extrae_el_id_solo_de_pedidos_validos():
    assert worker.federation_id_of(sobre("738")) == "738"
    assert worker.federation_id_of(sobre(738)) == "738"
    assert worker.federation_id_of(sobre("abc")) is None
    assert worker.federation_id_of(sobre("738", "rating.updated")) is None
    assert worker.federation_id_of("no es json") is None
    assert worker.federation_id_of("[]") is None


def test_procesa_los_pedidos_y_descarta_los_invalidos():
    vistos = []
    out = worker.handle_records([registro(sobre("1")), registro("basura", "m2"), {}], lambda fid: vistos.append(fid))
    assert out == {"lookups": 1}
    assert vistos == ["1"]


def test_si_la_consulta_falla_la_excepcion_sube_para_que_lambda_reintente():
    def lookup(_fid):
        raise RuntimeError("Federación caída")

    with pytest.raises(RuntimeError):
        worker.handle_records([registro(sobre("2"))], lookup)


def test_lambda_handler_detecta_entregas_de_sns(monkeypatch):
    llamados = []
    monkeypatch.setattr(cli, "execute", lambda mode, **kw: llamados.append((mode, kw["federation_id"])) or {})
    assert cli.lambda_handler({"Records": [registro(sobre("738"))]}, None) == {"lookups": 1}
    assert llamados == [("lookup", "738")]
