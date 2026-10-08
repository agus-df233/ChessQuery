"""Deja el stack local listo para presentar ChessQuery: club, torneos, sala y cuentas de demostración.

Uso: con ``make dev`` corriendo, ``make demo-seed``. Se puede repetir: busca lo que ya existe antes de crearlo.
Todo es ficticio (nombres inventados, correos @demo.chessquery.cl, sin RUT). Solo biblioteca estándar.

Entra con el mismo IdP simulado que la web: el formulario de login del flujo ``authorization_code`` (sujeto + claims,
como haría Entra) y después ``/token``. Luego llama a las mismas APIs que usa la web, por el proxy de Vite.
"""
from __future__ import annotations

import json
import os
import random
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date

BASE = os.environ.get("DEMO_BASE_URL", "http://localhost:5173")
IDP = os.environ.get("DEMO_IDP_URL", "http://localhost:8090/chessquery")
CLIENT_ID = os.environ.get("DEMO_CLIENT_ID", "chessquery-web")
REDIRECT = f"{BASE}/app"

FIRST = ["Tomás", "Valentina", "Matías", "Catalina", "Benjamín", "Javiera", "Vicente", "Antonia", "Joaquín",
         "Isidora", "Martín", "Florencia", "Agustín", "Josefa", "Lucas", "Emilia"]
LAST = ["González", "Muñoz", "Rojas", "Díaz", "Pérez", "Soto", "Contreras", "Silva", "Martínez", "Sepúlveda",
        "Morales", "Fuentes", "Hernández", "Torres", "Araya", "Flores"]

PEOPLE = {
    "organizadora": {"sub": "demo-organizadora", "given_name": "Profe", "family_name": "Demo"},
    "jugadora": {"sub": "demo-jugadora", "given_name": "Valentina", "family_name": "Nueva"},
    "jugador": {"sub": "demo-jugador", "given_name": "Tomás", "family_name": "Conectado"},
}


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):  # el código viaja en el Location del 302: no hay que seguirlo
        return None


def claims_of(person: dict) -> dict:
    email = f"{person['sub'].removeprefix('demo-')}@demo.chessquery.cl"
    return {"email": email, "email_verified": True, "given_name": person["given_name"], "family_name": person["family_name"]}


def login(person: dict) -> str:
    """Access token del IdP simulado para esa persona (mismo flujo que el navegador)."""
    query = urllib.parse.urlencode({"client_id": CLIENT_ID, "redirect_uri": REDIRECT, "response_type": "code",
                                    "scope": "openid profile email", "state": "demo", "nonce": "demo"})
    form = urllib.parse.urlencode({"username": person["sub"], "claims": json.dumps(claims_of(person))}).encode()
    opener = urllib.request.build_opener(_NoRedirect)
    try:
        opener.open(urllib.request.Request(f"{IDP}/authorize?{query}", data=form, method="POST"))
        raise SystemExit("El IdP simulado no devolvió el código de autorización")
    except urllib.error.HTTPError as e:  # 302 esperado
        location = e.headers.get("Location", "")
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)["code"][0]
    body = urllib.parse.urlencode({"grant_type": "authorization_code", "code": code, "redirect_uri": REDIRECT,
                                   "client_id": CLIENT_ID}).encode()
    with urllib.request.urlopen(urllib.request.Request(f"{IDP}/token", data=body)) as resp:
        return json.load(resp)["access_token"]


def call(token: str, method: str, path: str, body: dict | None = None, ok_missing: bool = False):
    """Llama a la API como la web. Con ``ok_missing`` un 404 devuelve None en vez de cortar la semilla."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, method=method,
                                 headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            text = resp.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        if ok_missing and e.code == 404:
            return None
        raise SystemExit(f"{method} {path} → {e.code} {e.read().decode()[:300]}") from None


def roster_rows() -> list[dict]:
    """16 jugadores ficticios con ELO nacional entre 1300 y 2100 (semilla fija: siempre los mismos)."""
    rng = random.Random(2026)
    return [{"firstName": FIRST[i], "lastName": f"{LAST[i]} Demo", "email": f"alumno{i + 1:02d}@demo.chessquery.cl",
             "eloNational": rng.randrange(1300, 2100, 10), "tags": ["demo"]} for i in range(16)]


def ensure_club(org: str) -> list[dict]:
    if call(org, "GET", "/api/organizations/me", ok_missing=True) is None:
        call(org, "POST", "/api/organizations", {"name": "Club Demo Andino", "city": "Santiago"})
    report = call(org, "POST", "/api/organizations/me/roster/import", {"rows": roster_rows()})
    print(f"  roster: {report['created']} nuevos, {report['duplicates']} ya estaban")
    return [p for p in call(org, "GET", "/api/organizations/me/roster") if "demo" in (p.get("tags") or [])]


def ensure_tournament(org: str, name: str, body: dict) -> dict:
    mine = call(org, "GET", "/api/tournaments/mine")["organized"]
    existing = next((t for t in mine if t["name"] == name and t["status"] != "FINISHED"), None)
    if existing:
        return existing
    return call(org, "POST", "/api/tournaments", {"name": name, "city": "Santiago", "region": "Metropolitana",
                                                  "startDate": date.today().isoformat(), **body})["tournament"]


def play_round(org: str, tid: int) -> None:
    """Genera la ronda siguiente y carga resultados verosímiles (gana el de más rating, a veces tablas)."""
    rnd = call(org, "POST", f"/api/tournaments/{tid}/rounds")
    for b in rnd["boards"]:
        if b["black"] is None or b["result"] is not None:
            continue
        white, black = (b["white"]["rating"] or 0), (b["black"]["rating"] or 0)
        result = "DRAW" if abs(white - black) < 60 else ("WHITE_WINS" if white > black else "BLACK_WINS")
        call(org, "PUT", f"/api/tournaments/{tid}/rounds/{rnd['number']}/boards/{b['board']}", {"result": result})


def ensure_league(org: str, roster: list[dict]) -> dict:
    """Torneo en curso con 2 rondas jugadas: la pantalla del monitor tiene datos desde el primer minuto."""
    t = ensure_tournament(org, "Liga Demo", {"format": "SWISS", "rounds": 4, "baseMinutes": 15, "incrementSeconds": 10})
    if t["status"] == "OPEN" and t["playerCount"] == 0:
        call(org, "POST", f"/api/tournaments/{t['id']}/registrations/bulk", {"playerIds": [p["id"] for p in roster[:8]]})
    while t["currentRound"] < 2:
        play_round(org, t["id"])
        t = call(org, "GET", f"/api/public/tournaments/{t['id']}")["tournament"]
    return t


def ensure_open(org: str, roster: list[dict]) -> dict:
    """Torneo abierto con cupo y acreditación por QR: se acredita en vivo durante la demo."""
    t = ensure_tournament(org, "Abierto Demo", {"format": "SWISS", "rounds": 5, "baseMinutes": 10, "incrementSeconds": 5,
                                                "maxPlayers": 24, "checkinRequired": True})
    if t["playerCount"] == 0:
        call(org, "POST", f"/api/tournaments/{t['id']}/registrations/bulk", {"playerIds": [p["id"] for p in roster[8:]]})
    return t


def ensure_room(org: str) -> dict:
    mine = call(org, "GET", "/api/rooms/mine")["organized"]
    existing = next((r for r in mine if r["name"] == "Clase 4°B" and r["status"] != "CLOSED"), None)
    return existing or call(org, "POST", "/api/rooms", {"name": "Clase 4°B", "boards": 4, "maxPlayers": 10,
                                                        "minutes": 10, "incrementSeconds": 0})


def ensure_friends(a: str, b: str, b_id: int, a_id: int) -> None:
    status = call(a, "GET", f"/api/friends/status/{b_id}")["status"]
    if status == "NONE":
        status = call(a, "POST", "/api/friends/requests", {"addresseeId": b_id})["status"]
    if status != "FRIENDS":
        request_id = call(b, "GET", f"/api/friends/status/{a_id}")["requestId"]
        call(b, "POST", f"/api/friends/requests/{request_id}/accept")


def main() -> None:
    print(f"== Semilla de demo contra {BASE}")
    tokens = {k: login(p) for k, p in PEOPLE.items()}
    ids = {k: call(t, "GET", "/api/users/me")["profile"]["id"] for k, t in tokens.items()}
    org = tokens["organizadora"]
    call(org, "PUT", "/api/users/me/profile", {"welcomed": True, "region": "Metropolitana"})

    roster = ensure_club(org)
    league = ensure_league(org, roster)
    open_t = ensure_open(org, roster)
    room = ensure_room(org)
    # Tomás ya pasó la bienvenida y vinculó Lichess y Chess.com (los dobles locales responden a usernames «e2e…»);
    # Valentina queda sin tocar: al entrar ve el asistente de bienvenida.
    call(tokens["jugador"], "PUT", "/api/users/me/profile", {
        "welcomed": True, "preferredCategory": "BLITZ", "region": "Valparaíso",
        "lichessUsername": "e2e_demo_tomas", "chesscomUsername": "e2e-demo-tomas"})
    ensure_friends(tokens["jugador"], tokens["jugadora"], ids["jugadora"], ids["jugador"])

    print("\n== Listo. Para entrar: botón «Entrar con mi correo», sujeto y claims (copiar y pegar):")
    for key, person in PEOPLE.items():
        print(f"  {key:13} sujeto: {person['sub']:18} claims: {json.dumps(claims_of(person), ensure_ascii=False)}")
    print("\n== Qué mostrar")
    print(f"  Monitor de la sala (Liga Demo): {BASE}/torneos/{league['id']}/pantalla")
    print(f"  Seguir a un jugador (apoderado): {BASE}/torneos/{league['id']}")
    print(f"  Acreditar con QR (Abierto Demo): {BASE}/club/torneos/{open_t['id']}/acreditacion")
    print(f"  Sala de clase «{room['name']}»: {BASE}/club/salas/{room['id']}  · código para alumnos: {room['code']}")
    print(f"  Bienvenida: entrar como {PEOPLE['jugadora']['sub']} → {BASE}/app")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.URLError as e:
        sys.exit(f"No hay stack local en {BASE} o {IDP} ({e.reason}). Levántalo con `make dev`.")
