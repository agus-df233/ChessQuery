"""Lectura de ratings públicos: Lichess (en bloque) y Chess.com (de a uno). Solo se leen ratings: nada personal."""
from __future__ import annotations

import json
import re
import urllib.parse

from .http import PoliteHttp

USERNAME = re.compile(r"^[A-Za-z0-9_-]{2,30}$")
LICHESS_BATCH = 300  # máximo que acepta POST /api/users

LICHESS_PERFS = {"bullet": "eloLichessBullet", "blitz": "eloLichessBlitz", "rapid": "eloLichessRapid",
                 "classical": "eloLichessClassical"}
CHESSCOM_MODES = {"chess_bullet": "eloChesscomBullet", "chess_blitz": "eloChesscomBlitz",
                  "chess_rapid": "eloChesscomRapid", "chess_daily": "eloChesscomDaily"}


def valid(username: str | None) -> bool:
    """Mismo formato que valida users al vincular: un username no puede cambiar la URL ni agregar parámetros."""
    return bool(username) and bool(USERNAME.match(username))


def lichess(http: PoliteHttp, base: str, usernames: list[str]) -> list[dict]:
    """Lichess: POST /api/users con hasta 300 usernames separados por coma → perfs.<modo>.rating."""
    players = []
    for i in range(0, len(usernames), LICHESS_BATCH):
        batch = usernames[i:i + LICHESS_BATCH]
        status, text = http.request("POST", f"{base}/api/users", ",".join(batch).encode(), "text/plain")
        if status != 200:
            continue  # un lote que falla no frena al resto; el próximo pedido diario lo reintenta
        for user in json.loads(text or "[]"):
            row = _ratings(user.get("perfs") or {}, LICHESS_PERFS, lambda v: v.get("rating"))
            if row and not user.get("disabled"):
                players.append({"lichessUsername": user.get("username") or user.get("id"), **row})
    return players


def chesscom(http: PoliteHttp, base: str, username: str) -> dict | None:
    """Chess.com: GET /pub/player/{u}/stats → chess_<modo>.last.rating. None si no existe o no responde."""
    status, text = http.request("GET", f"{base}/pub/player/{urllib.parse.quote(username.lower())}/stats")
    if status != 200:
        return None
    row = _ratings(json.loads(text or "{}"), CHESSCOM_MODES, lambda v: (v.get("last") or {}).get("rating"))
    return {"chesscomUsername": username, **row} if row else None


def _ratings(source: dict, fields: dict, read) -> dict:
    """Solo las modalidades con rating numérico (una cuenta nueva puede no tener ninguna)."""
    out = {}
    for mode, field in fields.items():
        value = read(source.get(mode) or {})
        if isinstance(value, int) and value > 0:
            out[field] = value
    return out
