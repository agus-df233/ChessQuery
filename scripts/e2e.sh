#!/usr/bin/env bash
# Recorridos E2E en el navegador contra el stack local completo (sin tocar servicios reales):
#   Postgres + LocalStack + IdP simulado (hace de Entra) · users/tournament/game · worker del ETL ·
#   Federación falsa (datos ficticios) · web (Vite) · Playwright (Chromium).
# Uso: make e2e   (levanta lo que falte, corre las pruebas y apaga lo que levantó, incluida la infra).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/apps/web/e2e/.resultados/logs"
mkdir -p "$LOGS"
PIDS=()

export OIDC_ISSUER_URI=http://localhost:8090/chessquery
export OIDC_AUDIENCE=default   # audiencia que emite el IdP simulado en el flujo del navegador
export AWS_ENDPOINT_URL=http://localhost:4566 AWS_REGION=us-east-1 AWS_DEFAULT_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test

cleanup() {
  echo "== Apagando lo que levantó el E2E"
  for pid in "${PIDS[@]}"; do pkill -P "$pid" 2>/dev/null || true; kill "$pid" 2>/dev/null || true; done
  docker compose -f "$ROOT/infra/docker-compose.yml" stop >/dev/null
}
trap cleanup EXIT

wait_http() { # url, nombre
  for _ in $(seq 1 90); do curl -sf "$1" >/dev/null && { echo "   $2 listo"; return 0; }; sleep 2; done
  echo "   $2 no respondió (ver $LOGS)"; return 1
}

echo "== Infra local"
docker compose -f "$ROOT/infra/docker-compose.yml" up -d --wait >/dev/null

echo "== Servicios Java"
mvn -B -ntp -q -f "$ROOT/pom.xml" -DskipTests install
for svc in users tournament game; do
  (cd "$ROOT/services/$svc" && exec mvn -B -ntp -q spring-boot:run) > "$LOGS/$svc.log" 2>&1 &
  PIDS+=($!)
done

echo "== Federación falsa y worker del ETL"
python3 "$ROOT/apps/web/e2e/support/federation_stub.py" > "$LOGS/federacion.log" 2>&1 &
PIDS+=($!)
make -C "$ROOT" etl-setup >/dev/null
(cd "$ROOT/etl" && FEDERATION_BASE_URL=http://localhost:8099 PRIVACY_PEPPER=dev-only-pepper-no-usar-en-cloud \
  PYTHONUNBUFFERED=1 exec .venv/bin/python -m chessquery_etl.federation.cli worker) > "$LOGS/worker.log" 2>&1 &
PIDS+=($!)

echo "== Web"
(cd "$ROOT/apps/web" && VITE_OIDC_AUTHORITY="$OIDC_ISSUER_URI" VITE_OIDC_CLIENT_ID=chessquery-web \
  VITE_OIDC_SCOPE="openid profile email" exec npx vite --port 5173 --strictPort) > "$LOGS/web.log" 2>&1 &
PIDS+=($!)

wait_http http://localhost:8081/actuator/health users
wait_http http://localhost:8082/actuator/health tournament
wait_http http://localhost:8083/actuator/health game
wait_http http://localhost:5173 web

echo "== Playwright"
cd "$ROOT/apps/web"
npx playwright install chromium >/dev/null
npx playwright test "$@"
