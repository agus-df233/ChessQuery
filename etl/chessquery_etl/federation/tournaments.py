"""Torneos publicados por la Federación (datos públicos del evento, sin personas).

Se trae la ficha del evento: nombre, ciudad, región, fechas, ritmo, rondas y si valida ELO nacional/FIDE.
Los participantes y resultados vinculan personas, así que quedan fuera hasta que exista convenio.
Se publica ``federation.tournament.published`` (docs/events.md); su consumidor será el servicio ``tournament``.
"""
from __future__ import annotations

from .client import FederationClient

EVENT_TYPE = "federation.tournament.published"

_FIELDS = ("id title city startDate endDate type rounds timeControl category eloN eloInt "
           "club { id name region { id name } }")
QUERY_RANKED = "{ rankedTournaments { " + _FIELDS + " } }"
QUERY_SEARCH = "query($word: String) { tournaments(word: $word) { " + _FIELDS + " } }"


def fetch(client: FederationClient, word: str | None = None) -> list[dict]:
    """Sin ``word``: los torneos rankeados recientes. Con ``word``: búsqueda por texto en todos los torneos."""
    if word:
        return client.query(QUERY_SEARCH, {"word": word}).get("tournaments") or []
    return client.query(QUERY_RANKED).get("rankedTournaments") or []


def to_event_tournament(t: dict) -> dict:
    """Fila validada → forma del evento (camelCase; ratedNational = eloN, ratedFide = eloInt).

    Sin ``sourceUrl``: la ruta pública de un torneo en el sitio no está confirmada; el id federativo sí lo está.
    """
    club = t.get("club") or {}
    region = club.get("region") or {}
    out = {
        "federationTournamentId": str(t["id"]),
        "title": t["title"].strip(),
        "city": (t.get("city") or "").strip().title() or None,
        "region": region.get("name"),
        "clubName": club.get("name"),
        "startDate": t["startDate"],
        "endDate": t.get("endDate") or t["startDate"],
        "type": t.get("type"),
        "rounds": t.get("rounds"),
        "timeControl": t.get("timeControl"),
        "category": t.get("category"),
        "ratedNational": bool(t.get("eloN")),
        "ratedFide": bool(t.get("eloInt")),
    }
    return {k: v for k, v in out.items() if v is not None}
