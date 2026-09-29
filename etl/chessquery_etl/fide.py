"""Parser de la lista combinada de FIDE (``players_list_foa.txt``, ancho fijo).

Las columnas se calculan desde el encabezado, no con posiciones fijas: si FIDE agrega o
desplaza una columna, el parser sigue funcionando mientras los nombres se mantengan.
De cada jugador se conserva lo mínimo para el ranking: FIDE solo publica el año de
nacimiento, y ChessQuery no pide más (Ley 21.719, minimización).
"""
from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Iterable, Iterator

COLUMNS = ["ID Number", "Name", "Fed", "Sex", "Tit", "WTit", "OTit", "FOA",
           "SRtng", "SGm", "SK", "RRtng", "RGm", "Rk", "BRtng", "BGm", "BK", "B-day", "Flag"]


@dataclass(frozen=True)
class FidePlayer:
    fide_id: str
    first_name: str
    last_name: str
    sex: str | None
    title: str | None
    birth_year: int | None
    standard: int | None
    rapid: int | None
    blitz: int | None
    inactive: bool

    def has_rating(self) -> bool:
        return any((self.standard, self.rapid, self.blitz))

    def as_dict(self) -> dict:
        return asdict(self)


def _int(value: str) -> int | None:
    value = value.strip()
    return int(value) if value.isdigit() and int(value) > 0 else None


def split_name(raw: str) -> tuple[str, str]:
    """FIDE publica "Apellidos, Nombres"; sin coma, todo queda como apellido."""
    last, sep, first = raw.partition(",")
    last, first = last.strip(), first.strip()
    return (first or "-", last) if sep else ("-", raw.strip())


def column_slices(header: str) -> dict[str, slice]:
    missing = [c for c in COLUMNS if c not in header]
    if missing:
        raise ValueError(f"Encabezado FIDE inesperado, faltan columnas: {missing}")
    starts = sorted((header.index(c), c) for c in COLUMNS)
    return {name: slice(start, starts[i + 1][0] if i + 1 < len(starts) else None)
            for i, (start, name) in enumerate(starts)}


def parse(lines: Iterable[str], federation: str = "CHI") -> Iterator[FidePlayer]:
    """Jugadores de una federación. ``lines`` incluye el encabezado como primera línea."""
    it = iter(lines)
    cols = column_slices(next(it))
    for line in it:
        if not line.strip() or line[cols["Fed"]].strip() != federation:
            continue
        get = lambda c: line[cols[c]].strip()  # noqa: E731
        first, last = split_name(get("Name"))
        yield FidePlayer(
            fide_id=get("ID Number"),
            first_name=first,
            last_name=last,
            sex=get("Sex") or None,
            title=get("Tit") or None,
            birth_year=_int(get("B-day")),
            standard=_int(get("SRtng")),
            rapid=_int(get("RRtng")),
            blitz=_int(get("BRtng")),
            inactive="i" in get("Flag"),
        )
