#!/usr/bin/env bash
# Recorridos E2E en el navegador (Playwright + Chromium) contra el stack local completo, sin servicios reales.
# Uso: make e2e   (levanta el stack, corre las pruebas y apaga todo al terminar, pase lo que pase).
set -euo pipefail
source "$(dirname "$0")/stack.sh"

start_stack

echo "== Playwright"
cd "$ROOT/apps/web"
npx playwright install chromium >/dev/null
npx playwright test "$@"
