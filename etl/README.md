# etl — ingesta de ratings y torneos

Trae datos de fuentes externas, los valida y minimiza, los deja en S3 y publica eventos en el tópico SNS
`chess-events`. **No escribe en la base de datos**: `users` consume los ratings y `tournament` el calendario de la
Federación.

Empieza por **`docs/etl/onboarding.md`**. Guía en PDF: `docs/etl/ChessQuery-ETL-guia-equipo.pdf`.

| Fuente | Módulo | Qué publica | Estado |
|---|---|---|---|
| FIDE (lista oficial mensual, federación CHI) | `fide.py` + `handler.py` | `rating.updated` (source `FIDE`) | ✅ probado con la lista real: 9.271 leídos, 4.192 con rating |
| Federación Chilena de Ajedrez — torneos | `federation/` | `federation.tournament.published` | ✅ en vivo |
| Federación — jugador puntual (lo pide el jugador al vincular su ficha) | `federation/worker.py` | `rating.updated` (source `FEDERACION`, con `rutHash`) | ✅ |
| Federación — jugadores masivo | `federation/` | `rating.updated` | ⛔ apagado hasta convenio |

```bash
make etl-setup                     # venv + dependencias
make test-etl                      # pytest (cobertura ≥ 90 %)
make etl-fide-local                # FIDE real → LocalStack
make federation-contract           # verifica el esquema de la Federación
make federation-tournaments-local  # torneos de la Federación → LocalStack
make federation-worker             # atiende las fichas que piden los jugadores (cola etl-federation-lookup)
```

En la nube cada fuente es una Lambda (módulo Terraform `etl-jobs`); ver `docs/etl/onboarding.md` §6.

Documentación: `docs/etl/onboarding.md` · `docs/etl/federacion.md` · `docs/etl/nueva-fuente.md` ·
`docs/etl/glosario.md` · contrato de eventos en `docs/events.md`.
