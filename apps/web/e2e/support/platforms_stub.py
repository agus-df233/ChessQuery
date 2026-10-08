"""Lichess y Chess.com falsos para el E2E: mismos endpoints públicos que usa la Lambda ``external-ratings``.

Uso: ``python apps/web/e2e/support/platforms_stub.py`` (puerto 8097) con ``LICHESS_API_BASE`` y ``CHESSCOM_API_BASE``
apuntando a ``http://localhost:8097``. Datos inventados: los usernames que empiezan con ``e2e`` tienen ratings fijos;
cualquier otro, "no existe".
"""
from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = 8097
LICHESS_PERFS = {"bullet": {"rating": 1777}, "blitz": {"rating": 1888}, "rapid": {"rating": 1999}}
CHESSCOM_STATS = {"chess_rapid": {"last": {"rating": 1666}}, "chess_daily": {"last": {"rating": 1555}}}


def known(username: str) -> bool:
    return username.lower().startswith("e2e")


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):  # noqa: N802 — Lichess: POST /api/users con usernames separados por coma
        names = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode().split(",")
        self._send(200, [{"id": n.lower(), "username": n, "perfs": LICHESS_PERFS} for n in names if known(n)])

    def do_GET(self):  # noqa: N802 — Chess.com: GET /pub/player/{u}/stats
        parts = self.path.strip("/").split("/")
        if len(parts) == 4 and parts[:2] == ["pub", "player"] and parts[3] == "stats" and known(parts[2]):
            self._send(200, CHESSCOM_STATS)
        else:
            self._send(404, {"code": 0, "message": "User not found"})

    def _send(self, status: int, body) -> None:
        out = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def log_message(self, *args):  # silencio: el E2E ya informa lo importante
        pass


if __name__ == "__main__":
    print(f"Lichess y Chess.com falsos en http://localhost:{PORT}")
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
