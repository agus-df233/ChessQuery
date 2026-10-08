# ETL de ChessQuery — onboarding

Guía para quien se suma a trabajar en `etl/`. En 10 minutos deberías tener el entorno andando y entender el
recorrido de un dato de punta a punta. Términos en `docs/etl/glosario.md`.

## 1. Qué hace el ETL (en una frase)

Trae ratings y torneos de **fuentes externas** (FIDE, la Federación Chilena de Ajedrez, Lichess y Chess.com), los **valida y
minimiza**, los guarda en **S3** y avisa por **SNS** para que el servicio `users` (y más adelante `tournament`)
los aplique. El ETL **nunca escribe en la base de datos**: publica eventos.

```
fuente externa ─► validar ─► minimizar ─► S3 staged/ ─► diff vs corrida anterior ─► SNS (lotes de 200) ─► SQS ─► users
                      └─► S3 rejected/ (solo id + motivo)                     └─► S3 manifests/ (conteos)
```

## 2. Entorno local en 10 minutos

Requisitos: Docker, Python 3.12+, Make.

```bash
make local-up                      # Postgres, LocalStack (SNS/SQS/S3), mock OIDC, Mailpit
make etl-setup                     # crea etl/.venv e instala el paquete + pytest
make test-etl                      # tests del ETL (cobertura mínima 90 %)
make etl-fide-local                # lista FIDE real → LocalStack (necesita `make users` corriendo para verla en la BD)
make federation-contract           # verifica el esquema de la Federación (no baja datos de personas)
make federation-tournaments-local  # torneos de la Federación en vivo → LocalStack
```

Para ver el efecto en la base: `make users` en otra terminal y luego `http://localhost:8081/api/public/ranking?type=FIDE_STANDARD`.

## 3. Mapa de carpetas

| Ruta | Qué es |
|---|---|
| `etl/chessquery_etl/pipeline.py` | Esqueleto común: `stage`, `changed_rows` (diff), `publish_batches`, `write_manifest`, `envelope` |
| `etl/chessquery_etl/fide.py` | Parser de la lista oficial de FIDE (columnas desde el encabezado) |
| `etl/chessquery_etl/handler.py` | Lambda y CLI de FIDE; adaptadores `S3Storage` y `SnsBus` |
| `etl/chessquery_etl/federation/` | Ingesta de la Federación (ver `docs/etl/federacion.md`) |
| `etl/chessquery_etl/external/` | Ratings públicos de Lichess (en bloque) y Chess.com (de a uno) de las cuentas vinculadas |
| `etl/tests/` | Tests con datos **sintéticos**; `federation_fakes.py` tiene los dobles de prueba |
| `docs/events.md` | **Contrato** de los eventos que publicamos (`rating.updated`, `federation.tournament.published`) |

## 4. Reglas que no se rompen (coordinar antes de cambiarlas)

1. **Contrato de eventos**: cualquier cambio en la forma de un evento se documenta primero en `docs/events.md` y se
   coordina con quien mantiene `users`/`tournament`.
2. **Lista blanca de campos** (`federation/contract.py`): define qué datos personales pedimos. Agregar un campo es
   una decisión de privacidad.
3. **El RUT nunca sale del proceso en claro**: se valida y se convierte en `rutHash` (`federation/privacy.py`). Si
   cambia ese cálculo, el match en `users` se rompe sin avisar: hay un test que lo compara con Java.
4. **El pepper no se cambia** sin recalcular los hashes existentes (vive en SSM; en local, `PRIVACY_PEPPER`).
5. **Descarga masiva de personas apagada** (`FEDERATION_BULK_PLAYERS_ENABLED=false`) hasta que exista convenio.
6. **Datos de prueba siempre ficticios**: nunca se commitean datos reales de personas (ni en fixtures ni en logs).

## 5. Cómo probar sin datos reales

Los tests usan transportes falsos (`FakeTransport`) y almacenamiento en memoria (`MemoryStorage`, `MemoryBus`): no
hay red ni AWS. Para una fuente nueva, crea fixtures sintéticas con la **forma real** de la fuente (ver
`tests/fixtures/players_list_sample.txt`, que tiene el encabezado real de FIDE con jugadores inventados).

## 6. Cómo se despliega

Cada fuente es una Lambda (`handler.lambda_handler` para FIDE, `federation.cli.lambda_handler` para la Federación),
definida en el módulo Terraform `infra/terraform/modules/etl-jobs`:

| Lambda | Cuándo corre | Qué hace |
|---|---|---|
| `fide-import` | día 2 de cada mes (regla de EventBridge) | lista FIDE (CHI) → `rating.updated` |
| `federation-tournaments` | todos los días | torneos de la Federación → `federation.tournament.published` |
| `federation-lookup` | cuando SNS le entrega `federation.lookup.requested` (sin cola) | ficha de un jugador que la vinculó → `rating.updated` |
| `external-ratings` | cuando SNS le entrega `external.ratings.sync.requested` (al vincular, al sincronizar y una vez al día) | ratings de Lichess y Chess.com → `rating.updated` (`LICHESS` / `CHESSCOM`) |

El código se empaqueta tal cual desde `etl/chessquery_etl` (solo usa `boto3`, que ya viene en el runtime de Lambda).
El pepper para el hash del RUT se lee de SSM (`PRIVACY_PEPPER_PARAM`), nunca de una variable en texto plano. Se usan
reglas de EventBridge y no Scheduler porque el Learner Lab no permite crear el rol que Scheduler necesita. En local
todo se corre con `make` (ver `etl/README.md`).

## 7. Siguientes lecturas

- `docs/etl/federacion.md` — la fuente más delicada: parámetros, validaciones y runbook.
- `docs/etl/nueva-fuente.md` — receta para agregar una fuente.
- `docs/etl/glosario.md` — vocabulario del equipo.
