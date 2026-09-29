import json

import pytest

from chessquery_etl.federation import cli, contract, ingest, players, tournaments
from chessquery_etl.federation.client import FederationClient
from chessquery_etl.federation.config import FederationConfig
from chessquery_etl.federation.privacy import Pepper
from chessquery_etl.federation.validation import (PERSON_RULES, TOURNAMENT_RULES, RejectionThresholdError,
                                                  run_rules)
from tests.federation_fakes import PEPPER, FakeTransport, MemoryBus, MemoryStorage, person, tournament

ENV = {"PRIVACY_PEPPER": PEPPER}


def client_with(data_by_query=None, **cfg):
    transport = FakeTransport(data_by_query)
    return FederationClient(FederationConfig(**cfg), transport=transport, sleep=lambda s: None), transport


# ── Validación ───────────────────────────────────────────────────────────────

def test_reglas_de_persona_rechazan_con_motivo_y_sin_pii():
    rows = [person("1"), person("2", identificator="12.345.678-9"), person("3", eloNat="99999"),
            person("4", firstName=" "), person("x"), person("5", title="XX"), person("1")]
    report = run_rules(rows, PERSON_RULES)
    assert [r["id"] for r in report.accepted] == ["1"]
    assert {r["motivo"] for r in report.rejected} == {"rut_invalido", "elo_fuera_de_rango", "nombre_vacio",
                                                      "id_invalido", "titulo_desconocido", "duplicado"}
    assert all(set(r) == {"id", "motivo"} for r in report.rejected)  # nunca datos personales en rejected/


def test_reglas_de_torneo():
    rows = [tournament("1"), tournament("2", endDate="2026-01-01"), tournament("3", startDate="ayer"),
            tournament("4", rounds=99), tournament("5", title=""), tournament("6", endDate=None)]
    report = run_rules(rows, TOURNAMENT_RULES)
    assert [r["id"] for r in report.accepted] == ["1", "6"]
    assert [r["motivo"] for r in report.rejected] == ["fin_antes_de_inicio", "fecha_invalida",
                                                     "rondas_fuera_de_rango", "torneo_sin_titulo"]


def test_umbral_de_rechazos_detiene_la_corrida():
    report = run_rules([person("1"), person("x")], PERSON_RULES)
    with pytest.raises(RejectionThresholdError, match="50.0%"):
        report.ensure_below(0.05)
    run_rules([], PERSON_RULES).ensure_below(0.05)  # corrida vacía no falla


# ── Minimización ─────────────────────────────────────────────────────────────

def test_persona_se_publica_minimizada():
    out = players.to_event_player(person("738", fideIdentificator="3404803", eloInter="1590", title="cm"),
                                  Pepper(PEPPER), "2026-10")
    assert out["federationId"] == "738" and out["fideId"] == "3404803" and out["title"] == "CM"
    assert out["firstName"] == "Ana María" and out["lastName"] == "Ficticia Prueba"
    assert out["birthYear"] == 2012 and "birthDate" not in out
    assert out["rutHash"] == Pepper(PEPPER).rut_hash("12.345.678-5") and "rut" not in out
    assert "identificator" not in json.dumps(out) and "12.345.678" not in json.dumps(out)
    assert out["eloNational"] == 1650 and out["eloFideStandard"] == 1590
    assert out["sourceUrl"].endswith("/player/738")
    sin_datos = players.to_event_player(person("9", identificator=None, fideIdentificator="0",
                                               birthdayFormated="sin fecha", eloNat="0"), Pepper(PEPPER), "2026-10")
    assert {"rutHash", "fideId", "birthYear", "eloNational"}.isdisjoint(sin_datos)


def test_torneo_se_publica_con_datos_del_evento():
    out = tournaments.to_event_tournament(tournament("3655"))
    assert out == {"federationTournamentId": "3655", "title": "TORNEO FICTICIO DE PRUEBA", "city": "Los Angeles",
                   "region": "Región del Biobío", "clubName": "Club Ficticio", "startDate": "2026-09-27",
                   "endDate": "2026-09-27", "type": "Rapido", "rounds": 5, "timeControl": "15+5",
                   "category": "OPEN", "ratedNational": True, "ratedFide": False}


# ── Descarga masiva apagada / consulta puntual ───────────────────────────────

def test_masivo_apagado_no_hace_ninguna_request():
    client, transport = client_with()
    with pytest.raises(players.BulkDisabledError, match="convenio"):
        players.bulk(client, client.config)
    assert transport.calls == []


def test_masivo_encendido_recorre_regiones_y_generos():
    client, transport = client_with({"regions": {"regions": [{"id": "7"}, {"id": "13"}]},
                                     "personRanking": {"personRanking": [person("1")]}},
                                    bulk_players_enabled=True, ranking_elo="nat")
    assert len(players.bulk(client, client.config)) == 4  # 2 regiones × 2 géneros
    ranking_calls = [c for c in transport.calls if "personRanking" in c["query"]]
    assert {(c["variables"]["region"], c["variables"]["gender"]) for c in ranking_calls} == \
           {("7", "M"), ("7", "F"), ("13", "M"), ("13", "F")}
    assert all("email" not in c["query"] and "canon" not in c["query"] for c in transport.calls)


def test_consulta_puntual():
    client, _ = client_with({"person(": {"person": person("738")}})
    assert [p["id"] for p in players.lookup(client, "738")] == ["738"]
    client, _ = client_with({"person(": {"person": None}})
    assert players.lookup(client, "0") == []


# ── Contrato ─────────────────────────────────────────────────────────────────

def test_contrato_ok_y_roto():
    client, _ = client_with()
    assert contract.check_contract(client)["PersonType"] >= len(contract.PERSON_FIELDS)
    broken = FederationClient(FederationConfig(), transport=FakeTransport(drop_fields=("eloNat", "rounds")),
                              sleep=lambda s: None)
    with pytest.raises(contract.ContractError, match="PersonType.eloNat.*TournamentType.rounds"):
        contract.check_contract(broken)
    assert set(contract.PERSON_FORBIDDEN).isdisjoint(contract.PERSON_FIELDS)


# ── Corridas completas ───────────────────────────────────────────────────────

def test_corrida_de_torneos_publica_solo_nuevos_o_modificados():
    storage, bus = MemoryStorage(), MemoryBus()
    cfg = FederationConfig()
    first = ingest.run_tournaments([tournament("1"), tournament("2"), tournament("x")], run_date="2026-09-28",
                                   config=FederationConfig(max_rejected_ratio=0.5), storage=storage, bus=bus)
    assert (first["accepted"], first["rejected"], first["changed"], first["batches"]) == (2, 1, 2, 1)
    event_type, body = bus.sent[0]
    assert event_type == body["eventType"] == "federation.tournament.published"
    assert "rejected/source=federation-tournaments/date=2026-09-28.jsonl" in storage.objects

    second = ingest.run_tournaments([tournament("1"), tournament("2", rounds=7)], run_date="2026-09-29",
                                    config=cfg, storage=storage, bus=bus)
    assert second["changed"] == 1 and bus.sent[-1][1]["payload"]["tournaments"][0]["rounds"] == 7


def test_corrida_de_jugadores_no_publica_si_supera_el_umbral():
    storage, bus = MemoryStorage(), MemoryBus()
    with pytest.raises(RejectionThresholdError):
        ingest.run_players([person("1"), person("x")], mode="bulk", period="2026-10", pepper=Pepper(PEPPER),
                           config=FederationConfig(), storage=storage, bus=bus)
    assert bus.sent == [] and any(k.startswith("rejected/") for k in storage.objects)


def test_corrida_de_jugadores_masiva_hace_diff_y_la_puntual_siempre_publica():
    storage, bus = MemoryStorage(), MemoryBus()
    kwargs = dict(period="2026-10", pepper=Pepper(PEPPER), config=FederationConfig(), storage=storage, bus=bus)
    assert ingest.run_players([person("1")], mode="bulk", **kwargs)["changed"] == 1
    assert ingest.run_players([person("1")], mode="bulk", **kwargs)["changed"] == 0
    assert ingest.run_players([person("1")], mode="lookup", **kwargs)["changed"] == 1
    payload = bus.sent[0][1]["payload"]
    assert payload["source"] == "FEDERACION" and payload["players"][0]["rutHash"]


# ── CLI / Lambda ─────────────────────────────────────────────────────────────

def test_cli_modos(monkeypatch, capsys):
    storage, bus = MemoryStorage(), MemoryBus()
    transport = FakeTransport({"rankedTournaments": {"rankedTournaments": [tournament("1")]},
                               "person(": {"person": person("738")}})
    client = FederationClient(FederationConfig(), transport=transport, sleep=lambda s: None)
    run = lambda mode, **kw: cli.execute(mode, ENV, client=client, sinks=(storage, bus), **kw)  # noqa: E731

    assert run("contract")["contract"] == "ok"
    assert run("tournaments")["changed"] == 1
    assert run("lookup", federation_id="738")["changed"] == 1
    with pytest.raises(players.BulkDisabledError):
        run("bulk")
    off = cli.execute("tournaments", {**ENV, "FEDERATION_TOURNAMENTS_ENABLED": "false"}, client=client,
                      sinks=(storage, bus))
    assert off == {"skipped": "FEDERATION_TOURNAMENTS_ENABLED=false"}

    monkeypatch.setattr(cli, "execute", lambda mode, **kw: {"mode": mode, **kw})
    cli.main(["lookup", "738"])
    assert json.loads(capsys.readouterr().out)["federation_id"] == "738"
    assert cli.lambda_handler({"mode": "tournaments", "word": "sub"}, None)["word"] == "sub"


def test_sinks_por_defecto_usan_boto3(monkeypatch):
    monkeypatch.setattr(cli.fide_handler, "_clients", lambda: ("s3", "sns"))
    storage, bus = cli._sinks({"ETL_BUCKET": "b", "CHESS_EVENTS_TOPIC_ARN": "arn:t"})
    assert (storage.bucket, bus.topic_arn) == ("b", "arn:t")


def test_busqueda_de_torneos_por_palabra_y_elo_no_numerico():
    client, transport = client_with({"tournaments(": {"tournaments": [tournament("9")]}})
    assert [t["id"] for t in tournaments.fetch(client, "primavera")] == ["9"]
    assert transport.calls[-1]["variables"] == {"word": "primavera"}
    assert run_rules([person("1", eloNat="mil")], PERSON_RULES).rejected[0]["motivo"] == "elo_fuera_de_rango"
