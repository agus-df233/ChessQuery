"""Parámetros de la ingesta de la Federación, leídos desde variables de entorno.

Toda perilla operativa vive acá para que el compañero del ETL (o la Lambda) cambie el comportamiento sin
tocar código. La tabla con cada variable, su valor por defecto y su efecto está en docs/etl/federacion.md.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Mapping

DEFAULT_BASE_URL = "https://www.federacionchilenadeajedrez.cl"


def _bool(value: str | None, default: bool) -> bool:
    return default if value is None else value.strip().lower() in {"1", "true", "si", "sí", "yes"}


def _regions(value: str | None) -> tuple[str, ...]:
    """"all" (o vacío) = todas las regiones que publique la Federación; si no, ids separados por coma."""
    if not value or value.strip().lower() == "all":
        return ()
    return tuple(v.strip() for v in value.split(",") if v.strip())


@dataclass(frozen=True)
class FederationConfig:
    base_url: str = DEFAULT_BASE_URL
    requests_per_second: float = 1.0        # cortesía: una request por segundo
    timeout_s: float = 30.0
    max_retries: int = 3                     # reintentos ante 429/5xx/red, con backoff exponencial + jitter
    breaker_threshold: int = 5               # fallos seguidos que abren el circuito y cortan la corrida
    user_agent: str = "ChessQuery-ETL/3 (+https://chessquery.cl; contacto@chessquery.cl)"
    bulk_players_enabled: bool = False       # descarga masiva de personas: SOLO con convenio firmado
    tournaments_enabled: bool = True         # torneos: datos públicos del evento
    regions: tuple[str, ...] = field(default_factory=tuple)
    ranking_elo: str = ""                    # argumento `elo` de personRanking: por confirmar con la Federación
    ranking_id: str = ""                     # argumento `id` de personRanking: por confirmar con la Federación
    max_rejected_ratio: float = 0.05         # si se rechaza más del 5 % de las filas, la corrida no publica

    @property
    def graphql_url(self) -> str:
        return self.base_url.rstrip("/") + "/graphql"

    @classmethod
    def from_env(cls, env: Mapping[str, str]) -> "FederationConfig":
        d = cls()
        return cls(
            base_url=env.get("FEDERATION_BASE_URL", d.base_url),
            requests_per_second=float(env.get("FEDERATION_RPS", d.requests_per_second)),
            timeout_s=float(env.get("FEDERATION_TIMEOUT_S", d.timeout_s)),
            max_retries=int(env.get("FEDERATION_MAX_RETRIES", d.max_retries)),
            breaker_threshold=int(env.get("FEDERATION_BREAKER_THRESHOLD", d.breaker_threshold)),
            user_agent=env.get("FEDERATION_USER_AGENT", d.user_agent),
            bulk_players_enabled=_bool(env.get("FEDERATION_BULK_PLAYERS_ENABLED"), d.bulk_players_enabled),
            tournaments_enabled=_bool(env.get("FEDERATION_TOURNAMENTS_ENABLED"), d.tournaments_enabled),
            regions=_regions(env.get("FEDERATION_REGIONS")),
            ranking_elo=env.get("FEDERATION_RANKING_ELO", d.ranking_elo),
            ranking_id=env.get("FEDERATION_RANKING_ID", d.ranking_id),
            max_rejected_ratio=float(env.get("FEDERATION_MAX_REJECTED_RATIO", d.max_rejected_ratio)),
        )

    def __post_init__(self) -> None:
        if self.requests_per_second <= 0 or self.requests_per_second > 5:
            raise ValueError("FEDERATION_RPS debe estar entre 0 y 5: es un sitio de terceros, no una API para carga")
        if not 0 <= self.max_rejected_ratio <= 1:
            raise ValueError("FEDERATION_MAX_REJECTED_RATIO debe estar entre 0 y 1")
