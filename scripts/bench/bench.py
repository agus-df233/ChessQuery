"""Benchmark HTTP v2 vs v3 del servicio de jugadores (stdlib, sin dependencias).

Para cada caso: calentamiento, luego N requests con C hilos (conexión keep-alive por hilo). Reporta p50/p95/p99,
RPS y errores; además compara que ambas versiones devuelvan el mismo top del ranking (paridad funcional) y mide la
latencia del bus de eventos (publicar elo.updated → fila actualizada en la BD). Resultado: JSON en stdout.
"""
from __future__ import annotations

import http.client
import json
import os
import statistics
import threading
import time
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

REQUESTS = int(os.environ.get("BENCH_REQUESTS", 2000))
CONCURRENCY = int(os.environ.get("BENCH_CONCURRENCY", 20))
WARMUP = int(os.environ.get("BENCH_WARMUP", 200))
V2, V3 = "localhost:18081", "localhost:28081"


def v3_token() -> str:
    """Token del mock OIDC con el issuer que ve el contenedor (host.docker.internal)."""
    body = urllib.parse.urlencode({"grant_type": "client_credentials", "client_id": "bench-user",
                                   "client_secret": "x", "scope": "chessquery-api"}).encode()
    req = urllib.request.Request("http://localhost:8090/chessquery/token", data=body,
                                 headers={"Host": "host.docker.internal:8090"})
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)["access_token"]


def cases(token: str) -> dict:
    auth = {"Authorization": f"Bearer {token}"}
    return {
        "ranking_global": ((V2, "/users/ranking?limit=50", {}), (V3, "/api/public/ranking?limit=50", {})),
        "ranking_region": ((V2, "/users/ranking?region=Metropolitana&limit=50", {}),
                           (V3, "/api/public/ranking?region=Metropolitana&limit=50", {})),
        "busqueda": ((V2, "/users/search?q=rojas%20soto&limit=20", {}),
                     (V3, "/api/users/search?q=rojas%20soto&limit=20", auth)),
        "perfil": ((V2, "/users/{id}/profile", {}), (V3, "/api/public/players/{id}", {})),
    }


_local = threading.local()


def _get(host: str, path: str, headers: dict) -> tuple[int, float]:
    conn = getattr(_local, host, None)
    if conn is None:
        conn = http.client.HTTPConnection(host, timeout=30)
        setattr(_local, host, conn)
    start = time.perf_counter()
    try:
        conn.request("GET", path, headers=headers)
        resp = conn.getresponse()
        resp.read()
        status = resp.status
    except (OSError, http.client.HTTPException):
        setattr(_local, host, None)
        status = 0
    return status, (time.perf_counter() - start) * 1000


def run_case(host: str, path: str, headers: dict) -> dict:
    paths = [path.replace("{id}", str(1 + i % 5000)) for i in range(REQUESTS)]
    with ThreadPoolExecutor(CONCURRENCY) as pool:
        list(pool.map(lambda p: _get(host, p, headers), paths[:WARMUP]))
        start = time.perf_counter()
        results = list(pool.map(lambda p: _get(host, p, headers), paths))
        elapsed = time.perf_counter() - start
    lat = sorted(ms for status, ms in results if status == 200)
    q = statistics.quantiles(lat, n=100) if len(lat) >= 2 else [0] * 99
    return {"ok": len(lat), "errors": REQUESTS - len(lat), "rps": round(len(lat) / elapsed, 1),
            "p50_ms": round(q[49], 1), "p95_ms": round(q[94], 1), "p99_ms": round(q[98], 1)}


def parity() -> dict:
    """Mismo dataset ⇒ misma secuencia de ELO en el top-50 y los mismos jugadores (ids). El orden dentro de un
    empate de ELO no es parte del contrato: v3 desempata por apellido; v2 no tiene orden estable."""
    def top(host, path):
        conn = http.client.HTTPConnection(host, timeout=30)
        conn.request("GET", path)
        return json.loads(conn.getresponse().read())
    out = {}
    for name, (a, b) in {"global": ("/users/ranking?limit=50", "/api/public/ranking?limit=50"),
                         "region": ("/users/ranking?region=Metropolitana&limit=50",
                                    "/api/public/ranking?region=Metropolitana&limit=50")}.items():
        v2, v3 = top(V2, a), top(V3, b)
        same_elos = [e["eloNational"] for e in v2] == [e["eloNational"] for e in v3]
        # ids comparables solo fuera del último empate (que puede cortar distinto en el puesto 50)
        cutoff = v3[-1]["eloNational"] if v3 else None
        ids = lambda xs: {e["playerId"] for e in xs if e["eloNational"] != cutoff}  # noqa: E731
        out[name] = {"misma_secuencia_elo": same_elos, "mismos_jugadores": ids(v2) == ids(v3), "n": len(v3)}
    return out


def _rating_via_http(host: str, path: str) -> int | None:
    conn = http.client.HTTPConnection(host, timeout=5)
    conn.request("GET", path)
    data = json.loads(conn.getresponse().read())
    return data.get("eloPlatform") if "eloPlatform" in data else (data.get("ratings") or {}).get("platform")


def _time_until(host: str, path: str, expected: int, publish, timeout: float = 20) -> float:
    """Milisegundos desde antes de publicar hasta que la API muestra el valor nuevo (sondeo cada 5 ms)."""
    start = time.perf_counter()
    publish()
    while time.perf_counter() - start < timeout:
        if _rating_via_http(host, path) == expected:
            return (time.perf_counter() - start) * 1000
        time.sleep(0.005)
    return float("nan")


def event_latency(samples: int = 20) -> dict:
    """Publicar elo.updated y medir hasta verlo en la API. v2: RabbitMQ (API HTTP). v3: SNS→SQS (LocalStack, boto3)."""
    import boto3  # correr con etl/.venv/bin/python (trae boto3)
    sns = boto3.client("sns", endpoint_url="http://localhost:4566", region_name="us-east-1",
                       aws_access_key_id="test", aws_secret_access_key="test")
    v2, v3 = [], []
    for i in range(samples):
        elo = 2000 + i
        event = {"eventId": str(uuid.uuid4()), "eventType": "elo.updated", "timestamp": "2026-09-28T00:00:00Z",
                 "payload": {"playerId": 1, "oldElo": 0, "newElo": elo, "delta": 1, "ratingType": "PLATFORM"}}
        body = json.dumps({"properties": {"content_type": "application/json"}, "routing_key": "elo.updated",
                           "payload": json.dumps(event), "payload_encoding": "string"}).encode()
        req = urllib.request.Request("http://localhost:15672/api/exchanges/%2F/ChessEvents/publish", data=body,
                                     headers={"Content-Type": "application/json",
                                              "Authorization": "Basic Y2hlc3NxdWVyeTpjaGVzc3F1ZXJ5X2Rldg=="})
        v2.append(_time_until(V2, "/users/1/profile", elo, lambda: urllib.request.urlopen(req).read()))
        v3_event = json.dumps(dict(event, eventId=str(uuid.uuid4())))
        v3.append(_time_until(V3, "/api/public/players/1", elo, lambda: sns.publish(
            TopicArn="arn:aws:sns:us-east-1:000000000000:chess-events", Message=v3_event,
            MessageAttributes={"eventType": {"DataType": "String", "StringValue": "elo.updated"}})))
    q = lambda xs: {"p50_ms": round(statistics.median(xs), 1), "max_ms": round(max(xs), 1)}  # noqa: E731
    return {"v2_rabbitmq": q(v2), "v3_sns_sqs": q(v3), "muestras": samples}


def main() -> None:
    token = v3_token()
    report = {"config": {"requests": REQUESTS, "concurrency": CONCURRENCY, "warmup": WARMUP}, "cases": {}}
    for name, ((h2, p2, hd2), (h3, p3, hd3)) in cases(token).items():
        report["cases"][name] = {"v2": run_case(h2, p2, hd2), "v3": run_case(h3, p3, hd3)}
    report["paridad_top50"] = parity()
    report["eventos"] = event_latency()
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
