#!/usr/bin/env bash
# La app completa en local para desarrollar o mostrarla, sin tenant de Entra ni servicios reales.
# Uso: make dev   → abrir http://localhost:5173 · Ctrl+C apaga todo (servicios, web e infra).
set -euo pipefail
source "$(dirname "$0")/stack.sh"

start_stack

if [ "${IDP:-0}" = 1 ]; then
  cat <<EOF

== Listo con el login real: http://localhost:5173  ("Continuar con Google")
   Issuer: $OIDC_ISSUER_URI
   Si algo responde 401, revisar el token en https://jwt.ms (iss, aud, email). Logs: .logs/ · Ctrl+C apaga todo.
EOF
else
cat <<'EOF'

== Listo: http://localhost:5173
   Entrar: "Entrar con mi correo" → en el IdP simulado escribe cualquier usuario (será tu cuenta) y en "claims":
     {"email":"ana@ejemplo.cl","email_verified":true,"given_name":"Ana","family_name":"Soto"}
   Otro jugador: otra ventana de incógnito con otro usuario (para desafiarse o inscribirse en un torneo).
   Ficha federativa de prueba: cualquier id que empiece con 7 (la Federación es falsa, datos ficticios).
   Logs: .logs/  ·  Correos: http://localhost:8025  ·  Ctrl+C para apagar todo.
EOF
fi

# Ctrl+C o una señal de término salen de inmediato (y el trap de stack.sh apaga todo). El sleep va en segundo
# plano con `wait` porque bash no atiende señales mientras espera a un comando en primer plano.
trap 'exit 0' INT TERM
while true; do sleep 3600 & wait $!; done
