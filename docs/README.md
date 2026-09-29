# Documentación de ChessQuery

## Vigente (se mantiene al día)

| Documento | Para qué |
|---|---|
| `adr/0001-arquitectura-v3.md` | Arquitectura de la v3 y por qué |
| `adr/0002-despliegue-aws-bajo-costo.md` | Despliegue en AWS, bus SNS/SQS, Terraform; enmiendas: Learner Lab, tiempo real por long polling, CI/Trivy/Dependabot |
| `events.md` | Catálogo de eventos entre servicios: **fuente de verdad**, se edita antes de codificar |
| `etl/onboarding.md` | Por dónde empezar si trabajas en el ETL |
| `etl/federacion.md` | Ingesta de la Federación: qué se trae, privacidad, parámetros, runbook |
| `etl/nueva-fuente.md` · `etl/glosario.md` | Receta para una fuente nueva y términos |
| `etl/ChessQuery-ETL-guia-equipo.pdf` | Los Markdown del ETL en PDF (se regenera con `make etl-docs`) |
| `verificacion/flujos-e2e.md` | Qué está verificado de punta a punta en el navegador y qué no |

## Fotos de un momento (no se actualizan)

| Documento | Fecha | Nota |
|---|---|---|
| `ChessQuery-v3-estado-proyecto.pdf` y `estado/` | 28-09-2026 | Estado antes de construir torneos y partidas |
| `verificacion/2026-09-28-decisiones.md` | 28-09-2026 | Verificación de las decisiones de arquitectura en esa fecha |
| `verificacion/benchmark-v2-vs-v3.md` (+ `.json`) | 28-09-2026 | Rendimiento del servicio de jugadores v2 vs v3 |
