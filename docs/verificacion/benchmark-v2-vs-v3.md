# Benchmark v2 vs v3 — servicio de jugadores (28-09-2026)

**Pregunta:** ¿la v3 responde igual de bien que la arquitectura anterior?
**Respuesta corta:** sí, y mejor en lo que más se usa. En ranking y búsqueda la v3 duplica el rendimiento con
la mitad de latencia y devuelve los mismos resultados. Hay dos regresiones medidas, ambas acotadas: perfil
individual más lento bajo carga y más memoria en reposo.

## Cómo se midió (reproducible)

- Mismo hardware (Mac M-series, Docker), **cada servicio en su contenedor con 1 CPU y 1 GB**.
- **Mismo dataset:** 10.000 jugadores sintéticos con semilla fija (`scripts/bench/seed.py`), cargados en ambas BD.
- v2: imagen `infrastructure-ms-users` (repo ChessQuery_FS3) + Postgres 16 + RabbitMQ.
  v3: imagen Jib actual + Postgres 16 + LocalStack (SNS/SQS) + mock OIDC (la búsqueda de la v3 valida JWT).
- `scripts/bench/bench.py`: 200 requests de calentamiento + 2.000 medidas, 20 hilos con keep-alive.
- Datos crudos: `docs/verificacion/benchmark-2026-09-28.json`. Pasos: `scripts/bench/README.md`.

## Resultados (segunda corrida, JVM calientes)

| Caso | v2 RPS | v3 RPS | v2 p50 / p95 | v3 p50 / p95 | Errores |
|---|---|---|---|---|---|
| Ranking global (top 50) | 95 | **188** | 199 / 315 ms | **102 / 181 ms** | 0 / 0 |
| Ranking por región | 119 | **235** | 183 / 281 ms | **86 / 166 ms** | 0 / 0 |
| Búsqueda difusa (v3 con JWT) | 81 | **214** | 229 / 361 ms | **87 / 170 ms** | 0 / 0 |
| Perfil de un jugador | **1.049** | 400 | **5 / 82 ms** | 24 / 100 ms | 0 / 0 |

| Otra medición | v2 | v3 |
|---|---|---|
| Arranque aislado (1 CPU) | 16,4 s | 17,7 s |
| Memoria en reposo | 252 MiB | 404 MiB |
| Memoria tras la carga | 340 MiB | 533 MiB |
| Evento `elo.updated` → visible en la API (p50 / máx, 20 muestras) | 18 / 63 ms (RabbitMQ) | 26 / 58 ms (SNS→SQS, LocalStack) |

**Paridad funcional:** con el mismo dataset, el top-50 global y el regional tienen **la misma secuencia de ELO y
los mismos jugadores**. Dos diferencias son intencionales: la v3 desempata por apellido (la v2 no tiene orden
estable en empates) y abrevia el apellido de menores sin cuenta (Ley 21.719).

## Lectura

- **Ranking y búsqueda (lo que más se usa en la demo y en torneos): la v3 es ~2× más rápida.** Lo explican las
  consultas nativas con índices propios (trigramas para la búsqueda, índice de ranking) y la categoría por año
  de nacimiento con índice simple.
- **Perfil individual: la v3 es más lenta bajo carga** (400 contra 1.049 RPS; en secuencia es ~8 ms contra ~5 ms).
  Hace más trabajo por request: la cadena de seguridad OAuth2 también evalúa rutas públicas y hay una consulta
  extra por el título vigente. Mejora propuesta (no aplicada): traer el título en la misma consulta y cachear
  los perfiles públicos 5 min (el header `Cache-Control` ya está; en la nube lo cachea el borde).
- **Memoria: la v3 usa ~60 % más** (SDK de AWS, cliente SQS con long polling y `MaxRAMPercentage=75`, que deja
  crecer el heap). Con 1 GB por tarea sobra margen; si se quiere bajar a 512 MB, fijar `-Xmx` y medir de nuevo.
- **Eventos:** ambos buses entregan en decenas de milisegundos. La cifra de la v3 es de LocalStack; en AWS real
  SQS con long polling suele agregar decenas de ms, irrelevante para este dominio (ratings, notificaciones).
- **Variabilidad:** la primera corrida (JVM más frías) dio la misma tendencia con números peores para ambas
  (ranking global: 37 contra 109 RPS). Repetir la medición antes de citar cifras absolutas en el pitch.

## Límites de esta medición

Es local y de un solo servicio; no incluye red de AWS, API Gateway ni ALB. Sirve para comparar arquitecturas
en igualdad de condiciones, no para dimensionar producción. Para eso: repetir con `scripts/bench/bench.py`
contra el entorno Academy una vez desplegado.
