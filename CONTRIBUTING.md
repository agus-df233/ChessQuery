# Cómo trabajar en ChessQuery

Guía para el equipo. Las reglas técnicas completas están en `CLAUDE.md` (sirven para personas y agentes) y las
decisiones de arquitectura en `docs/adr/`.

## Ramas y flujo

- **`main`**: lo estable y presentable. Solo recibe cambios desde `develop` por PR.
- **`develop`**: integración del día a día. Nadie hace push directo con cambios grandes: se trabaja en una rama.
- **Ramas de trabajo** desde `develop`, con prefijo según el tipo y nombre corto en español:
  `feat/torneos-inscripcion-qr`, `fix/reloj-tema-oscuro`, `etl/fuente-ajefech`, `docs/guia-organizador`,
  `infra/lambdas-etl`.

```bash
git switch develop && git pull
git switch -c feat/mi-cambio
# … cambios + pruebas …
git push -u origin feat/mi-cambio      # y abrir un PR hacia develop
```

Un PR se integra cuando el **CI está en verde** y alguien más del equipo lo revisó.

## Commits

- En **español**, asunto corto que diga qué cambia para el producto o el equipo, con prefijo del área:
  `torneos: …`, `partidas: …`, `jugadores: …`, `etl: …`, `web: …`, `infra: …`, `ci: …`, `docs: …`.
- Cuerpo opcional con 2–4 viñetas si aporta contexto.
- **Sin trailers de co-autoría** (`Co-Authored-By`, etc.), aunque uses un agente de IA.

## Antes de abrir un PR

- [ ] `make test` en verde (Java con cobertura ≥ 90 % por módulo, ETL, web con axe).
- [ ] Si tocaste pantallas o flujos: `make e2e`.
- [ ] `make complexity` (CCN ≤ 10 por función); si tocaste Terraform, `make tf-check`.
- [ ] Evento nuevo o cambiado → primero en `docs/events.md`; cola nueva → `infra/events/topology.json`.
- [ ] Herramienta nueva en el CI o decisión de arquitectura → un ADR o una enmienda en `docs/adr/`.
- [ ] Nada de credenciales, `.env`, `*.tfvars` ni datos personales reales (en pruebas, solo datos ficticios).
- [ ] Comentarios y documentación en español latino.

## Qué corre el CI

En cada push a `main`/`develop` y en cada PR (`.github/workflows/ci.yml`): Java, ETL, web (tests + build),
Terraform, complejidad (informativo) y Trivy (bloquea dependencias con vulnerabilidades HIGH/CRITICAL que ya tengan
corrección). Dependabot propone actualizaciones una vez por semana en un PR agrupado contra `develop`.

## AWS

Nadie aplica cambios de infraestructura desde su equipo sin acordarlo: se propone con `terraform plan` y lo aplica
la persona a cargo (`make academy-*`, ver `Makefile`). Nunca compartir ni commitear credenciales; la configuración
vive en SSM Parameter Store.

## ¿Dónde está cada cosa?

`README.md` (panorama y comandos) · `docs/README.md` (índice de la documentación) · `docs/events.md` (contratos
entre servicios) · `docs/etl/onboarding.md` (si trabajas en el ETL) · `docs/verificacion/flujos-e2e.md` (qué está
verificado de punta a punta).
