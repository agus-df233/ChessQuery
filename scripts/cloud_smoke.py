"""Pruebas de humo de ChessQuery desplegado, sin login: ¿la entrada está sana y cerrada como corresponde?

Solo hace pedidos HTTP a la propia app (GET/POST sin datos y un handshake WebSocket). No llama a las APIs de AWS
ni necesita credenciales. Biblioteca estándar.

Uso:
  make cloud-smoke                                          # toma las URL de `terraform output` del Learner Lab
  python3 scripts/cloud_smoke.py --base http://localhost:5173 --ws ws://localhost:5173/ws   # contra make dev

Sale con código 1 si alguna verificación falla. Los tiempos se informan (desde Chile suma la latencia a us-east-1).
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import socket
import ssl
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

TIMEOUT = 15
SPA_MARKER = '<div id="root">'


@dataclass
class Reply:
    status: int
    headers: dict
    body: str
    ms: float


def fetch(url: str, method: str = "GET") -> Reply:
    req = urllib.request.Request(url, method=method, headers={"Accept": "*/*", "User-Agent": "chessquery-smoke"})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:  # noqa: S310 (URL de la propia app)
            body, status, headers = r.read().decode("utf-8", "replace"), r.status, dict(r.headers)
    except urllib.error.HTTPError as e:
        body, status, headers = e.read().decode("utf-8", "replace"), e.code, dict(e.headers)
    return Reply(status, {k.lower(): v for k, v in headers.items()}, body, (time.perf_counter() - start) * 1000)


def is_json_from_service(r: Reply) -> bool:
    return "application/json" in r.headers.get("content-type", "") and r.body.lstrip().startswith(("{", "["))


class Report:
    def __init__(self):
        self.failures = 0

    def check(self, name: str, ok: bool, detail: str = "") -> None:
        self.failures += 0 if ok else 1
        print(f"  {'OK   ' if ok else 'FALLA'}  {name}{f' · {detail}' if detail else ''}")


def check_spa(rep: Report, base: str) -> None:
    """La web y sus rutas profundas (QR, pantalla, invitación) entregan la SPA (S3 responde index.html)."""
    for path in ["/", "/torneos", "/torneos/1/pantalla", "/app/reclamar/xyz", "/app/desafio/xyz"]:
        r = fetch(base + path)
        rep.check(f"web {path}", SPA_MARKER in r.body and r.status in (200, 404), f"HTTP {r.status}")


def check_public_api(rep: Report, base: str) -> None:
    for path in ["/api/public/ranking?limit=10", "/api/public/tournaments", "/api/public/tournaments/calendar"]:
        r = fetch(base + path)
        rep.check(f"API pública {path}", r.status == 200 and is_json_from_service(r), f"HTTP {r.status}")
    r = fetch(base + "/api/public/ranking?limit=1")
    rep.check("cabecera X-Content-Type-Options: nosniff", r.headers.get("x-content-type-options") == "nosniff")
    rep.check("cabecera X-Frame-Options: DENY", r.headers.get("x-frame-options", "").upper() == "DENY")


def check_closed_doors(rep: Report, base: str) -> None:
    r = fetch(base + "/api/users/me")
    rep.check("/api/users/me sin token → 401", r.status == 401, f"HTTP {r.status}")
    r = fetch(base + "/api/tournaments/mine")
    rep.check("/api/tournaments/mine sin token → 401", r.status == 401, f"HTTP {r.status}")
    r = fetch(base + "/internal/players/1")
    rep.check("/internal no se alcanza desde internet", not is_json_from_service(r), f"HTTP {r.status}")
    r = fetch(base + "/api/public/tournaments/999999999")
    body = _json(r.body)
    rep.check("404 con el formato común de error", r.status == 404 and {"status", "error", "message", "timestamp"} <= set(body),
              f"HTTP {r.status}")


def check_alb(rep: Report, alb: str) -> None:
    """Quien se salta API Gateway llega al ALB sin la cabecera de origen: debe recibir 404."""
    r = fetch(f"http://{alb}/api/public/ranking?limit=1")
    rep.check("ALB directo sin cabecera de origen → 404", r.status == 404 and not is_json_from_service(r), f"HTTP {r.status}")


def check_ws(rep: Report, ws_url: str) -> None:
    """WebSocket sin token: API Gateway rechaza el handshake o el servidor cierra con 1008 (política)."""
    try:
        status, close_code = ws_handshake(ws_url)
    except OSError as e:
        rep.check("WebSocket sin token rechazado", False, f"sin conexión: {e}")
        return
    rejected = status != 101 or close_code == 1008
    rep.check("WebSocket sin token rechazado", rejected, f"handshake {status}, cierre {close_code}")


def ws_handshake(ws_url: str) -> tuple[int, int | None]:
    u = urllib.parse.urlparse(ws_url)
    port = u.port or (443 if u.scheme == "wss" else 80)
    sock = socket.create_connection((u.hostname, port), timeout=TIMEOUT)
    if u.scheme == "wss":
        sock = ssl.create_default_context().wrap_socket(sock, server_hostname=u.hostname)
    key = base64.b64encode(os.urandom(16)).decode()
    sock.sendall((f"GET {u.path or '/'} HTTP/1.1\r\nHost: {u.hostname}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    data = _read_until(sock, b"\r\n\r\n")
    status = int(data.split(b" ", 2)[1])
    close_code = _close_code(sock, data.split(b"\r\n\r\n", 1)[1]) if status == 101 else None
    sock.close()
    return status, close_code


def _read_until(sock, marker: bytes) -> bytes:
    data = b""
    while marker not in data:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
    return data


def _close_code(sock, pending: bytes) -> int | None:
    """Lee el primer frame del servidor; si es de cierre (opcode 8), devuelve su código."""
    data = pending
    while len(data) < 4:
        chunk = sock.recv(4096)
        if not chunk:
            return None
        data += chunk
    opcode = data[0] & 0x0F
    return int.from_bytes(data[2:4], "big") if opcode == 0x8 else None


def measure(rep: Report, base: str, samples: int) -> None:
    """Tiempos de las lecturas públicas vistas desde este computador (incluye la red hasta us-east-1)."""
    for path in ["/api/public/ranking?limit=50", "/api/public/tournaments/calendar"]:
        times = sorted(fetch(base + path).ms for _ in range(samples))
        p95 = times[max(0, int(len(times) * 0.95) - 1)]
        print(f"  INFO   tiempo {path}: p50 {statistics.median(times):.0f} ms · p95 {p95:.0f} ms ({samples} muestras)")


def _json(text: str) -> dict:
    try:
        value = json.loads(text)
        return value if isinstance(value, dict) else {}
    except json.JSONDecodeError:
        return {}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", required=True, help="URL de la app (app_url)")
    parser.add_argument("--alb", help="DNS del ALB (alb_dns_name); se omite en local")
    parser.add_argument("--ws", help="URL del WebSocket (ws_url)")
    parser.add_argument("--samples", type=int, default=20)
    args = parser.parse_args()
    base = args.base.rstrip("/")
    rep = Report()
    print(f"== Humo contra {base}")
    check_spa(rep, base)
    check_public_api(rep, base)
    check_closed_doors(rep, base)
    if args.alb:
        check_alb(rep, args.alb)
    if args.ws:
        check_ws(rep, args.ws)
    measure(rep, base, args.samples)
    print(f"== {'Todo bien' if rep.failures == 0 else f'{rep.failures} verificación(es) fallaron'}")
    return 1 if rep.failures else 0


if __name__ == "__main__":
    sys.exit(main())
