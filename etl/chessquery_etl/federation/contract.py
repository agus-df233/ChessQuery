"""Lista blanca de campos y chequeo de contrato contra el esquema real de la Federación.

La lista blanca es la decisión de privacidad más importante del ETL: define *qué pedimos*. Lo que no está
acá nunca se descarga (por ejemplo ``email``, ``canon*`` de pago de cuotas o flags de administración, que el
tipo ``PersonType`` sí expone). Antes de bajar datos, ``check_contract`` confirma por introspección que
estos campos siguen existiendo: si la Federación cambia su esquema, la corrida falla sin tocar datos.
"""
from __future__ import annotations

from .client import FederationClient, FederationError

# Campos que SÍ pedimos, por tipo. Cambiar esto es una decisión de privacidad: coordinar antes (docs/etl/federacion.md).
PERSON_FIELDS = ("id", "firstName", "secondName", "lastName", "lastNameSecond", "gender", "title",
                 "fideIdentificator", "eloNat", "eloInter", "birthdayFormated", "identificator", "clubBasic")
CLUB_FIELDS = ("id", "name", "region")
REGION_FIELDS = ("id", "name")
TOURNAMENT_FIELDS = ("id", "title", "city", "startDate", "endDate", "type", "rounds", "timeControl",
                     "category", "eloN", "eloInt", "club")

# Campos que JAMÁS se piden aunque existan. Se listan para que una revisión los vea explícitamente.
PERSON_FORBIDDEN = ("email", "username", "canon", "canonConfirm", "canon2026", "canonConfirmado2026",
                    "requestedChangeClub", "isAdmin", "canAccess", "adminId")

EXPECTED = {"PersonType": PERSON_FIELDS, "ClubType": CLUB_FIELDS, "RegionType": REGION_FIELDS,
            "TournamentType": TOURNAMENT_FIELDS}

_TYPE_FIELDS = "query($name: String!) { __type(name: $name) { fields { name } } }"


class ContractError(FederationError):
    """El esquema de la Federación ya no tiene un campo que necesitamos."""


def missing_fields(available: set[str], expected: tuple[str, ...]) -> list[str]:
    return [f for f in expected if f not in available]


def check_contract(client: FederationClient) -> dict[str, int]:
    """Introspección de los tipos usados. Devuelve {tipo: n° de campos}; lanza ContractError si falta alguno."""
    problems, sizes = [], {}
    for type_name, expected in EXPECTED.items():
        data = client.query(_TYPE_FIELDS, {"name": type_name})
        available = {f["name"] for f in ((data.get("__type") or {}).get("fields") or [])}
        sizes[type_name] = len(available)
        problems += [f"{type_name}.{f}" for f in missing_fields(available, expected)]
    if problems:
        raise ContractError(f"Campos que ya no existen en la Federación: {', '.join(problems)}")
    return sizes
