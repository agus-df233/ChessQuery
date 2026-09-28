# etl — ratings externos (paso 5)

Importa la **lista oficial de FIDE** (federación CHI), la guarda en S3 y publica `rating.updated`
en el tópico SNS `chess-events`. `users` consume el evento y hace el match (solo por
identificadores, nunca por nombre) y aplica la lista de supresión. El ETL no toca la base de datos
ni necesita VPC (ADR-0002).

```
players_list.zip (FIDE, mensual) ─► raw/source=fide/period=YYYY-MM/          (lifecycle 30 días)
                                  ─► staged/source=fide/period=YYYY-MM/players.jsonl
                                  ─► diff contra el período anterior
                                  ─► SNS rating.updated en lotes de 200 (solo cambios)
                                  ─► manifests/source=fide/period=YYYY-MM.json
```

Lista real al 27-09-2026: 9.271 jugadores CHI, 4.192 con rating (21 lotes; ~2 s en local).
De cada uno se publica lo mínimo: nombre, FIDE id, **año** de nacimiento (FIDE no publica más),
ratings standard/rapid/blitz, URL de la ficha y período.

## Local (contra LocalStack)

```bash
python3 -m venv etl/.venv && etl/.venv/bin/pip install -e 'etl[dev]'
etl/.venv/bin/pytest -q etl                      # cobertura ≥ 90 %
make etl-fide-local                              # descarga la lista real y la publica en LocalStack
# o con la fixture sintética:
etl/.venv/bin/python -m chessquery_etl.handler --file etl/tests/fixtures/players_list_sample.txt --period 2026-10
```

La fixture `tests/fixtures/players_list_sample.txt` usa el encabezado real de FIDE con jugadores
**ficticios**.

## Nube

`chessquery_etl.handler.lambda_handler` es el entrypoint de la Lambda (Python 3.12+), programada con
EventBridge Scheduler el día 2 de cada mes. Variables: `ETL_BUCKET`, `CHESS_EVENTS_TOPIC_ARN` y,
opcional, `FIDE_LIST_URL`. Pendiente: módulo Terraform `jobs` (Lambda + Scheduler + lifecycle de `raw/`).

## Fuentes pendientes

- **Federación (AJEFECH):** ID federativo, ELO nacional, club. Carga masiva **solo con convenio**;
  sin convenio, consulta puntual cuando el titular vincula su ID federativo (su consentimiento).
  Hay un scraper probado en la v2 (`ms-etl/app/sources/ajefech_scraper.py`).
- **Lichess / Chess.com:** sync nocturno de los usernames vinculados (endpoint bulk de Lichess).
