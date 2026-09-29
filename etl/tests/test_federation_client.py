import json

import pytest

from chessquery_etl.federation.client import CircuitOpenError, FederationClient, FederationError, urllib_transport
from chessquery_etl.federation.config import FederationConfig
from tests.federation_fakes import FakeTransport


class FakeClock:
    def __init__(self):
        self.now, self.slept = 0.0, []

    def __call__(self):
        return self.now

    def sleep(self, seconds):
        self.slept.append(round(seconds, 3))
        self.now += seconds


def make(transport, **cfg):
    clock = FakeClock()
    return FederationClient(FederationConfig(**cfg), transport=transport, clock=clock, sleep=clock.sleep), clock


def test_respeta_el_ritmo_y_se_identifica():
    transport = FakeTransport({"regions": {"regions": []}})
    client, clock = make(transport, requests_per_second=0.5)
    client.query("{ regions { id } }")
    client.query("{ regions { id } }")
    assert clock.slept == [2.0]  # 0,5 rps → al menos 2 s entre requests
    assert transport.calls[0]["headers"]["User-Agent"].startswith("ChessQuery-ETL")
    assert transport.calls[0]["url"].endswith("/graphql")


def test_reintenta_errores_transitorios_con_backoff():
    ok = (200, json.dumps({"data": {"x": 1}}))
    client, clock = make(FakeTransport(script=[(503, ""), (429, ""), ok]), max_retries=3)
    assert client.query("{ x }") == {"x": 1}
    assert len([s for s in clock.slept if s >= 1]) == 2  # dos esperas de backoff (1 s y 2 s + jitter)


def test_no_reintenta_errores_definitivos_ni_respuestas_con_errors():
    client, _ = make(FakeTransport(script=[(400, "")]))
    with pytest.raises(FederationError, match="HTTP 400"):
        client.query("{ x }")
    client, _ = make(FakeTransport(script=[(200, json.dumps({"errors": [{"message": "campo inválido"}]}))]))
    with pytest.raises(FederationError, match="errores"):
        client.query("{ x }")


def test_circuito_se_abre_tras_fallos_seguidos():
    client, _ = make(FakeTransport(script=[(503, "")] * 10), max_retries=1, breaker_threshold=2)
    with pytest.raises(FederationError):
        client.query("{ x }")
    with pytest.raises(CircuitOpenError):
        client.query("{ x }")


def test_errores_de_red_se_tratan_como_transitorios():
    def caido(*_):
        raise OSError("sin red")
    client, _ = make(caido, max_retries=0)
    with pytest.raises(FederationError, match="HTTP 503"):
        client.query("{ x }")


def test_config_desde_env_y_limites():
    cfg = FederationConfig.from_env({"FEDERATION_RPS": "2", "FEDERATION_BULK_PLAYERS_ENABLED": "true",
                                     "FEDERATION_REGIONS": "7, 13", "FEDERATION_TOURNAMENTS_ENABLED": "no"})
    assert (cfg.requests_per_second, cfg.bulk_players_enabled, cfg.regions, cfg.tournaments_enabled) == \
           (2.0, True, ("7", "13"), False)
    assert FederationConfig.from_env({}).bulk_players_enabled is False  # por defecto: apagado
    assert FederationConfig.from_env({"FEDERATION_REGIONS": "all"}).regions == ()
    with pytest.raises(ValueError, match="FEDERATION_RPS"):
        FederationConfig(requests_per_second=50)
    with pytest.raises(ValueError, match="REJECTED"):
        FederationConfig(max_rejected_ratio=2)


def test_urllib_transport_devuelve_status_de_error(monkeypatch):
    import io
    import urllib.error

    def falla(req, timeout):
        raise urllib.error.HTTPError(req.full_url, 502, "bad", {}, io.BytesIO(b"upstream"))

    monkeypatch.setattr("urllib.request.urlopen", falla)
    assert urllib_transport("https://example.invalid/graphql", b"{}", {}, 1) == (502, "upstream")

    class Resp(io.BytesIO):
        status = 200

        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

    monkeypatch.setattr("urllib.request.urlopen", lambda req, timeout: Resp(b'{"data":{}}'))
    assert urllib_transport("https://example.invalid/graphql", b"{}", {}, 1) == (200, '{"data":{}}')
