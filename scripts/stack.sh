#!/usr/bin/env bash
# Stack local completo, compartido por `make dev` (scripts/dev.sh) y `make e2e` (scripts/e2e.sh):
#   Postgres + LocalStack + IdP simulado (hace de Entra) · users/tournament/game · worker del ETL ·
#   Federación falsa (datos ficticios, sin tocar el sitio real) · web (Vite en :5173).
# Se usa con `source`: define start_stack y un trap que apaga todo lo que se levantó (incluida la infra).

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOGS="$ROOT/.logs"
PIDS=()

export OIDC_ISSUER_URI=http://localhost:8090/chessquery
export OIDC_AUDIENCE=default   # audiencia que emite el IdP simulado en el flujo del navegador
export AWS_ENDPOINT_URL=http://localhost:4566 AWS_REGION=us-east-1 AWS_DEFAULT_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test

stop_stack() {
  echo "== Apagando el stack local"
  for pid in "${PIDS[@]}"; do pkill -P "$pid" 2>/dev/null || true; kill "$pid" 2>/dev/null || true; done
  docker compose -f "$ROOT/infra/docker-compose.yml" stop >/dev/null
}

wait_http() { # url, nombre
  for _ in $(seq 1 90); do curl -sf "$1" >/dev/null && { echo "   $2 listo"; return 0; }; sleep 2; done
  echo "   $2 no respondió (ver $LOGS)"; return 1
}

start_stack() {
  mkdir -p "$LOGS"
  trap stop_stack EXIT

  echo "== Infra local (Postgres, LocalStack, IdP simulado, Mailpit)"
  docker compose -f "$ROOT/infra/docker-compose.yml" up -d --wait >/dev/null

  echo "== Servicios Java (logs en .logs/)"
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
}
