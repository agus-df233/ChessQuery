# Benchmark v2 vs v3

Compara el servicio de jugadores de la v2 (repo `ChessQuery_FS3`) con `services/users` de la v3, en contenedores
con 1 CPU y 1 GB y el mismo dataset sintético. Resultado vigente: `docs/verificacion/benchmark-v2-vs-v3.md`.

```bash
make local-up                         # Postgres, LocalStack, mock OIDC de la v3
make image                            # imagen chessquery/users con el código actual
docker exec chessquery-postgres-1 psql -U chessquery -d chessquery -c "CREATE DATABASE chessquery_bench"
docker compose -f scripts/bench/compose.yml up -d   # v2 (18081) y v3 (28081); esperar /actuator/health
python3 scripts/bench/seed.py         # 10.000 jugadores idénticos en ambas BD
etl/.venv/bin/python scripts/bench/bench.py > resultado.json   # usa boto3 del venv del ETL
docker compose -f scripts/bench/compose.yml down -v
```

Requiere la imagen local `infrastructure-ms-users:latest` de la v2. Variables opcionales: `BENCH_REQUESTS`,
`BENCH_CONCURRENCY`, `BENCH_WARMUP`.
