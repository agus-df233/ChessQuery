import json

import pytest

from chessquery_etl.external import handler, sources
from chessquery_etl.external.http import CircuitOpenError, PoliteHttp

LICHESS_USERS = [
    {"id": "anasoto", "username": "AnaSoto", "perfs": {"bullet": {"rating": 1810}, "blitz": {"rating": 1905},
                                                     "rapid": {"rating": 2001}, "classical": {"rating": 0}}},
    {"id": "cerrada", "username": "Cerrada", "disabled": True, "perfs": {"blitz": {"rating": 1500}}},
    {"id": "nueva", "username": "Nueva", "perfs": {}},
]
CHESSCOM_STATS = {"chess_blitz": {"last": {"rating": 1750}}, "chess_daily": {"last": {"rating": 1600}},
                  "chess_rapid": {"record": {"win": 3}}}


class FakeTransport:
    """Responde según la URL; guarda cada llamada para revisar ritmo, método y cuerpo."""

    def __init__(self, responses):
        self.responses, self.calls = responses, []

    def __call__(self, method, url, body, headers, timeout):
        self.calls.append((method, url, body, headers))
        answer = self.responses(method, url)
        if isinstance(answer, Exception):
            raise answer
        return answer


def http(transport, **kw):
    return PoliteHttp(transport=transport, clock=lambda: 0.0, sleep=lambda s: None, **kw)


class FakeBus:
    def __init__(self):
        self.published = []

    def publish(self, event_type, body):
        self.published.append((event_type, json.loads(body)))


def sns(accounts, event_type=handler.EVENT_SYNC_REQUESTED):
    message = json.dumps({"eventId": "e", "eventType": event_type, "timestamp": "2026-10-07T00:00:00Z",
                          "payload": {"accounts": accounts}})
    return {"EventSource": "aws:sns", "Sns": {"MessageId": "m", "Message": message}}


def test_lichess_en_bloque_solo_ratings_validos_y_cuentas_activas():
    t = FakeTransport(lambda m, u: (200, json.dumps(LICHESS_USERS)))
    rows = sources.lichess(http(t), "https://li", ["AnaSoto", "Cerrada", "Nueva"])
    assert rows == [{"lichessUsername": "AnaSoto", "eloLichessBullet": 1810, "eloLichessBlitz": 1905, "eloLichessRapid": 2001}]
    method, url, body, headers = t.calls[0]
    assert (method, url, body) == ("POST", "https://li/api/users", b"AnaSoto,Cerrada,Nueva")
    assert headers["Content-Type"] == "text/plain" and "ChessQuery" in headers["User-Agent"]


def test_lichess_parte_en_lotes_de_300_y_sigue_si_un_lote_falla():
    calls = []

    def answer(m, u):
        calls.append(u)
        return (500, "") if len(calls) <= 4 else (200, "[]")
    users = [f"u{i}" for i in range(301)]
    assert sources.lichess(http(FakeTransport(answer), max_retries=3), "https://li", users) == []
    assert len(calls) == 5  # el primer lote reintenta 3 veces y se rinde; el segundo responde


def test_chesscom_por_usuario_con_ritmo_y_cuenta_inexistente():
    def answer(m, u):
        return (200, json.dumps(CHESSCOM_STATS)) if "luispaz" in u else (404, "{}")
    t = FakeTransport(answer)
    waits = []
    client = PoliteHttp(min_interval=0.5, transport=t, clock=lambda: 0.0, sleep=waits.append)
    assert sources.chesscom(client, "https://cc", "LuisPaz") == {
        "chesscomUsername": "LuisPaz", "eloChesscomBlitz": 1750, "eloChesscomDaily": 1600}
    assert sources.chesscom(client, "https://cc", "noexiste") is None
    assert t.calls[0][1] == "https://cc/pub/player/luispaz/stats"
    assert waits == [0.5]  # la segunda consulta espera el ritmo mínimo


def test_reintenta_errores_de_red_y_corta_el_circuito():
    t = FakeTransport(lambda m, u: OSError("sin red"))
    client = http(t, max_retries=2, breaker_threshold=5)
    assert client.request("GET", "https://cc/x") == (503, "")
    assert len(t.calls) == 3
    with pytest.raises(CircuitOpenError):  # al quinto fallo seguido se corta, a mitad de la segunda consulta
        client.request("GET", "https://cc/y")
    assert len(t.calls) == 5


def test_usernames_validos_sin_repetir_y_mensajes_ajenos_descartados():
    records = [sns([{"lichessUsername": "AnaSoto", "chesscomUsername": "LuisPaz"}, {"lichessUsername": "AnaSoto"},
                    {"lichessUsername": "../admin"}, {"chesscomUsername": "x"}]),
               sns([], event_type="rating.updated"),
               {"Sns": {"Message": "no es json"}}]
    assert handler.accounts_of(records) == (["AnaSoto"], ["LuisPaz"])
    assert not sources.valid(None) and not sources.valid("a b") and sources.valid("ab")


def test_lambda_publica_rating_updated_por_fuente():
    bus = FakeBus()
    lichess = http(FakeTransport(lambda m, u: (200, json.dumps(LICHESS_USERS[:1]))))
    chesscom = http(FakeTransport(lambda m, u: (200, json.dumps(CHESSCOM_STATS))))
    summary = handler.run(["AnaSoto"], ["LuisPaz"], bus, lichess_base="https://li", chesscom_base="https://cc",
                          lichess_http=lichess, chesscom_http=chesscom)
    assert summary == {"lichess": 1, "chesscom": 1, "requested": {"lichess": 1, "chesscom": 1}}
    assert [(t, e["payload"]["source"]) for t, e in bus.published] == [("rating.updated", "LICHESS"), ("rating.updated", "CHESSCOM")]
    assert bus.published[0][1]["payload"]["players"][0]["eloLichessRapid"] == 2001


def test_lambda_handler_de_punta_a_punta_con_bus_inyectado(monkeypatch):
    bus = FakeBus()
    monkeypatch.setattr(sources, "lichess", lambda h, base, users: [{"lichessUsername": users[0], "eloLichessBlitz": 1900}])
    monkeypatch.setattr(sources, "chesscom", lambda h, base, u: None)
    out = handler.lambda_handler({"Records": [sns([{"lichessUsername": "AnaSoto", "chesscomUsername": "Nadie"}])]}, None,
                                 env={"LICHESS_API_BASE": "https://li"}, bus=bus)
    assert out["lichess"] == 1 and out["chesscom"] == 0
    assert bus.published[0][1]["payload"] == {"source": "LICHESS", "players": [{"lichessUsername": "AnaSoto", "eloLichessBlitz": 1900}]}
    assert handler.lambda_handler({}, None, env={}, bus=bus)["requested"] == {"lichess": 0, "chesscom": 0}
