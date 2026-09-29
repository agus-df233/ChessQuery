"""Jugadores federados: consulta puntual (consentida) y descarga masiva (apagada sin convenio).

- ``lookup``: un jugador por su id federativo, cuando la persona vincula su ficha en ChessQuery. Es la vía
  habilitada hoy porque la base legal es el consentimiento del titular.
- ``bulk``: todos los jugadores por región vía ``personRanking``. Solo corre con
  ``FEDERATION_BULK_PLAYERS_ENABLED=true``, que se activa cuando exista convenio con la Federación.

En ambos casos se pide solo la lista blanca (``contract.PERSON_FIELDS``) y se minimiza antes de publicar:
año de nacimiento en vez de fecha y hash del RUT en vez del RUT (ver ``privacy.py``).
"""
from __future__ import annotations

from datetime import datetime

from .client import FederationClient
from .config import FederationConfig
from .privacy import Pepper

SOURCE = "FEDERACION"
PROFILE_URL = "https://www.federacionchilenadeajedrez.cl/player/{}"

_PERSON = """id firstName secondName lastName lastNameSecond gender title fideIdentificator eloNat eloInter
             birthdayFormated identificator clubBasic { id name region { id name } }"""
QUERY_PERSON = "query($id: String!) { person(id: $id) { " + _PERSON + " } }"
QUERY_RANKING = ("query($id: String!, $gender: String!, $elo: String!, $region: String!) "
                 "{ personRanking(id: $id, gender: $gender, elo: $elo, region: $region) { " + _PERSON + " } }")
QUERY_REGIONS = "{ regions { id name } }"
GENDERS = ("M", "F")


class BulkDisabledError(Exception):
    """Se intentó la descarga masiva con el flag apagado."""


def lookup(client: FederationClient, federation_id: str) -> list[dict]:
    """Ficha de un jugador (lista de 0 o 1 elemento, para usar el mismo flujo que ``bulk``)."""
    person = client.query(QUERY_PERSON, {"id": str(federation_id)}).get("person")
    return [person] if person else []


def bulk(client: FederationClient, config: FederationConfig) -> list[dict]:
    """Todas las personas por región y género. Lanza BulkDisabledError si el flag está apagado (sin requests)."""
    if not config.bulk_players_enabled:
        raise BulkDisabledError("Descarga masiva apagada: requiere convenio (FEDERATION_BULK_PLAYERS_ENABLED)")
    regions = config.regions or tuple(r["id"] for r in client.query(QUERY_REGIONS).get("regions") or [])
    people: list[dict] = []
    for region in regions:
        for gender in GENDERS:
            variables = {"id": config.ranking_id, "gender": gender, "elo": config.ranking_elo, "region": region}
            people += client.query(QUERY_RANKING, variables).get("personRanking") or []
    return people


def birth_year(birthday: str | None) -> int | None:
    """La Federación publica "dd/mm/aaaa"; solo guardamos el año (alcanza para la categoría)."""
    try:
        return datetime.strptime((birthday or "").strip(), "%d/%m/%Y").year
    except ValueError:
        return None


def _join(*parts: str | None) -> str:
    return " ".join(p.strip() for p in parts if p and p.strip())


def _positive(value) -> int | None:
    number = int(value or 0)
    return number if number > 0 else None


def to_event_player(person: dict, pepper: Pepper, period: str) -> dict:
    """Fila validada → forma de ``rating.updated`` (docs/events.md). El RUT sale de aquí solo como hash."""
    rut = person.get("identificator")
    club = person.get("clubBasic") or {}
    fide = (person.get("fideIdentificator") or "").strip()
    out = {
        "federationId": str(person["id"]),
        "firstName": _join(person.get("firstName"), person.get("secondName")),
        "lastName": _join(person.get("lastName"), person.get("lastNameSecond")),
        "fideId": fide if fide and fide != "0" else None,
        "title": (person.get("title") or "").strip().upper() or None,
        "rutHash": pepper.rut_hash(rut) if rut else None,
        "birthYear": birth_year(person.get("birthdayFormated")),
        "clubName": club.get("name"),
        "eloNational": _positive(person.get("eloNat")),
        "eloFideStandard": _positive(person.get("eloInter")),
        "sourceUrl": PROFILE_URL.format(person["id"]),
        "period": period,
    }
    return {k: v for k, v in out.items() if v is not None}
