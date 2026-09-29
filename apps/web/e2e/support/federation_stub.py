"""GraphQL falso de la Federación para el E2E: mismo contrato que la real, con personas y torneos ficticios.

Uso: ``python apps/web/e2e/support/federation_stub.py`` (puerto 8099) y ``FEDERATION_BASE_URL=http://localhost:8099``.
Nunca contiene datos reales: los ids 7xx devuelven una ficha inventada; cualquier otro id, "no existe".
"""
from __future__ import annotations

import json
import pathlib
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[4] / "etl"))
from tests.federation_fakes import person, schema_data, tournament  # noqa: E402

PORT = 8099


def answer(request: dict) -> dict:
    query, variables = request.get("query", ""), request.get("variables") or {}
    if "__type" in query:
        return schema_data(variables["name"])
    if "person(" in query:
        pid = str(variables.get("id", ""))
        # Adulta ficticia con RUT ficticio válido (DV correcto); la abreviatura de menores la prueban users y tournament
        return {"person": person(pid, firstName="Jugadora", secondName="", lastName="Federada", lastNameSecond="E2E",
                                 identificator="22.222.222-2", eloNat="1720", birthdayFormated="04/03/1995")
                if pid.startswith("7") else None}
    if "ournaments" in query:  # rankedTournaments o tournaments(word)
        key = "rankedTournaments" if "rankedTournaments" in query else "tournaments"
        return {key: [tournament("9001", title="ABIERTO FICTICIO E2E", startDate="2026-12-05", endDate="2026-12-06")]}
    return {}


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):  # noqa: N802 (nombre de la API de http.server)
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        out = json.dumps({"data": answer(body)}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(out)))
        self.end_headers()
        self.wfile.write(out)

    def log_message(self, *args):  # silencio: el E2E ya informa lo importante
        pass


if __name__ == "__main__":
    print(f"Federación falsa en http://localhost:{PORT}/graphql")
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
