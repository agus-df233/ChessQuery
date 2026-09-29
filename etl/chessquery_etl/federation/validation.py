"""Validación fila a fila y control de la corrida.

Cada regla es una función chica que devuelve el *motivo* del rechazo (o ``None`` si la fila pasa). Las filas
rechazadas se guardan en ``rejected/`` solo con su id y el motivo, nunca con datos personales. Si se rechaza
más del umbral configurado (5 % por defecto), la corrida se detiene antes de publicar: casi siempre significa
que la fuente cambió de formato y conviene revisarlo a mano.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import date
from typing import Callable

from .privacy import rut_is_valid

TITLES = {"GM", "IM", "FM", "CM", "WGM", "WIM", "WFM", "WCM"}
ELO_MIN, ELO_MAX = 800, 3000  # 0 = "sin rating" (válido); fuera de 800..3000 es dato corrupto
ROUNDS_MIN, ROUNDS_MAX = 1, 30

Rule = Callable[[dict], "str | None"]


class RejectionThresholdError(Exception):
    """Se rechazaron demasiadas filas: la corrida no publica."""


@dataclass
class Report:
    accepted: list[dict] = field(default_factory=list)
    rejected: list[dict] = field(default_factory=list)  # [{"id": ..., "motivo": ...}] sin PII

    @property
    def total(self) -> int:
        return len(self.accepted) + len(self.rejected)

    @property
    def rejected_ratio(self) -> float:
        return len(self.rejected) / self.total if self.total else 0.0

    def ensure_below(self, max_ratio: float) -> None:
        if self.rejected_ratio > max_ratio:
            raise RejectionThresholdError(
                f"{len(self.rejected)}/{self.total} filas rechazadas ({self.rejected_ratio:.1%}) superan el "
                f"{max_ratio:.0%} permitido: revisar docs/etl/federacion.md#runbook")


def run_rules(rows: list[dict], rules: list[Rule]) -> Report:
    """Aplica las reglas en orden; la primera que falla define el motivo. Deduplica por ``id``."""
    report, seen = Report(), set()
    for row in rows:
        reason = next((r for r in (rule(row) for rule in rules) if r), None)
        if reason is None and row.get("id") in seen:
            reason = "duplicado"
        if reason:
            report.rejected.append({"id": row.get("id"), "motivo": reason})
        else:
            seen.add(row["id"])
            report.accepted.append(row)
    return report


# ── Reglas comunes ───────────────────────────────────────────────────────────

def id_numerico(row: dict) -> str | None:
    return None if re.fullmatch(r"\d+", str(row.get("id") or "")) else "id_invalido"


def _elo(value) -> int | None:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return None


# ── Reglas de personas ───────────────────────────────────────────────────────

def nombre_completo(row: dict) -> str | None:
    return None if (row.get("firstName") or "").strip() and (row.get("lastName") or "").strip() else "nombre_vacio"


def rut_valido_si_existe(row: dict) -> str | None:
    rut = row.get("identificator")
    return None if not rut or rut_is_valid(rut) else "rut_invalido"


def elos_en_rango(row: dict) -> str | None:
    for key in ("eloNat", "eloInter"):
        elo = _elo(row.get(key))
        if elo is None or (elo != 0 and not ELO_MIN <= elo <= ELO_MAX):
            return "elo_fuera_de_rango"
    return None


def titulo_conocido(row: dict) -> str | None:
    title = (row.get("title") or "").strip().upper()
    return None if not title or title in TITLES else "titulo_desconocido"


PERSON_RULES: list[Rule] = [id_numerico, nombre_completo, rut_valido_si_existe, elos_en_rango, titulo_conocido]


# ── Reglas de torneos ────────────────────────────────────────────────────────

def titulo_de_torneo(row: dict) -> str | None:
    return None if (row.get("title") or "").strip() else "torneo_sin_titulo"


def fechas_coherentes(row: dict) -> str | None:
    try:
        start = date.fromisoformat(row.get("startDate") or "")
        end = date.fromisoformat(row.get("endDate") or row["startDate"])
    except (ValueError, KeyError):
        return "fecha_invalida"
    return None if start <= end else "fin_antes_de_inicio"


def rondas_en_rango(row: dict) -> str | None:
    rounds = row.get("rounds")
    return None if rounds is None or ROUNDS_MIN <= int(rounds) <= ROUNDS_MAX else "rondas_fuera_de_rango"


TOURNAMENT_RULES: list[Rule] = [id_numerico, titulo_de_torneo, fechas_coherentes, rondas_en_rango]
