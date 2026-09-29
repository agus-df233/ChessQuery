"""Dobles de prueba para la Federación: transporte GraphQL falso y datos sintéticos (personas ficticias)."""
import json

from chessquery_etl.federation import contract

PEPPER = "test-pepper-0123456789"  # el mismo que usa application-test.yml del servicio users


def person(pid: str, **over) -> dict:
    """Persona ficticia con la forma de PersonType (solo los campos de la lista blanca)."""
    base = {"id": pid, "firstName": "Ana", "secondName": "María", "lastName": "Ficticia", "lastNameSecond": "Prueba",
            "gender": "F", "title": "", "fideIdentificator": "0", "eloNat": "1650", "eloInter": "0",
            "birthdayFormated": "04/03/2012", "identificator": "12.345.678-5",
            "clubBasic": {"id": "9", "name": "Club Ficticio", "region": {"id": "7", "name": "Región Metropolitana"}}}
    base.update(over)
    return base


def tournament(tid: str, **over) -> dict:
    base = {"id": tid, "title": "TORNEO FICTICIO DE PRUEBA", "city": "LOS ANGELES", "startDate": "2026-09-27",
            "endDate": "2026-09-27", "type": "Rapido", "rounds": 5, "timeControl": "15+5", "category": "OPEN",
            "eloN": True, "eloInt": False,
            "club": {"id": "3", "name": "Club Ficticio", "region": {"id": "11", "name": "Región del Biobío"}}}
    base.update(over)
    return base


def schema_data(type_name: str, drop: tuple = ()) -> dict:
    fields = [f for f in contract.EXPECTED[type_name] if f not in drop] + ["email", "canon"]
    return {"__type": {"fields": [{"name": f} for f in fields]}}


class FakeTransport:
    """Responde según la consulta. ``script`` permite simular fallos: lista de (status, body) consumida en orden."""

    def __init__(self, data_by_query: dict | None = None, script: list | None = None, drop_fields: tuple = ()):
        self.calls: list[dict] = []
        self.data_by_query = data_by_query or {}
        self.script = list(script or [])
        self.drop_fields = drop_fields

    def __call__(self, url, body, headers, timeout):
        request = json.loads(body)
        self.calls.append({"url": url, "headers": headers, **request})
        if self.script:
            return self.script.pop(0)
        return 200, json.dumps({"data": self._data(request)})

    def _data(self, request):
        if "__type" in request["query"]:
            return schema_data(request["variables"]["name"], self.drop_fields)
        return next((v for k, v in self.data_by_query.items() if k in request["query"]), {})


class MemoryStorage:
    def __init__(self):
        self.objects: dict[str, str] = {}

    def get_text(self, key):
        return self.objects.get(key)

    def put_text(self, key, body):
        self.objects[key] = body


class MemoryBus:
    def __init__(self):
        self.sent: list[tuple[str, dict]] = []

    def publish(self, event_type, body):
        self.sent.append((event_type, json.loads(body)))
