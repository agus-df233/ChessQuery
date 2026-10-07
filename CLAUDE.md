# ChessQuery — instrucciones para agentes

Plataforma chilena de ajedrez competitivo (v3). Monorepo: Java 21 / Spring Boot 3.5 (`libs/`,
`services/`), Python 3.12+ (`etl/`), React 18 + TypeScript (`apps/web`, `packages/ui-lib`), Terraform
(`infra/terraform`). Decisiones en `docs/adr/` (0001 arquitectura, 0002 despliegue AWS); estas
instrucciones y los ADR mandan sobre cualquier regla genérica de más abajo.

## Comandos

```bash
make dev               # app completa en :5173 con IdP simulado y Federación falsa; Ctrl+C apaga todo
make local-up          # Postgres 16 · LocalStack 4.14 (SNS/SQS/S3) · Mailpit
make users             # servicio users contra la infra local (requiere OIDC_ISSUER_URI/OIDC_AUDIENCE)
make tournament        # servicio tournament en :8082 (necesita users)
make game              # servicio game en :8083 (necesita users)
make web               # web en :5173; el proxy enruta por prefijo como el ALB (torneos → 8082, partidas → 8083, resto → 8081)
make etl-fide-local    # lista FIDE real (CHI) → LocalStack → users
make test              # mvn clean verify + pytest + vitest (siempre `clean`: target/ guarda recursos viejos)
make e2e               # Playwright: recorridos del jugador y del organizador contra el stack local completo
make complexity        # CCN ≤ 10 por función (umbral del equipo)
make image             # imagen de users con Jib (arm64)
make tf-check          # terraform fmt + validate de todos los entornos
```

## Reglas del proyecto

- **Identidad:** siempre de `@CurrentUser UserPrincipal` (token de Entra External ID), nunca del body ni de la
  URL. No existen contraseñas en la app: login/registro/recuperación son del IdP.
- **Datos personales (Ley 21.719):** a terceros nunca RUT, email, fecha de nacimiento ni género; menores
  abreviados vía `PublicNames`; de terceros solo `birth_year` y `rut_hash` (`IdentifierHasher`). Match con
  fuentes externas solo por identificadores, nunca por nombre. Respetar `data_suppression`.
- **Eventos:** envelope `ChessEvent` en el tópico SNS `chess-events`, una cola SQS por consumidor con filter
  policy por `eventType` y DLQ; consumidores idempotentes (`IdempotentConsumer`). Las Lambdas del ETL reciben
  directo de SNS, sin cola (sección `lambdas` de la topología). Todo evento nuevo se documenta primero en
  `docs/events.md`. La topología es infra (`infra/events/topology.json`, módulos `messaging` y `etl-jobs`).
- **Persistencia:** un schema PostgreSQL por servicio, sin FKs entre schemas; Flyway es dueño del esquema,
  Hibernate solo valida.
- **Errores REST:** `{ status, error, message, timestamp }`; JSON camelCase, columnas snake_case.
- **Calidad:** cobertura ≥ 90 % por módulo (gate de CI); accesibilidad AA con axe en la web.
- **CI** (`.github/workflows/ci.yml`, en push a `main`/`develop` y PRs): Java, ETL, web, Terraform, complejidad y
  Trivy. Acciones de terceros **fijadas a un commit exacto**, no a una etiqueta. Dependabot: 1 PR semanal agrupado
  contra `develop`, sin versiones mayores (ADR-0002, enmienda 2026-09-29). Herramientas nuevas en el CI se declaran
  primero en un ADR.
- **Git:** `main` protegida; se trabaja en `develop` y ramas de feature, todo por PR con CI verde. Commits en español, cortos y
  con foco funcional, sin trailers de co-autoría.
- **Idioma:** comentarios, docstrings y documentación en español latino.

## AWS en este proyecto

- **IaC = Terraform** (ADR-0002), no CDK ni CloudFormation. Un solo código, dos entornos:
  `envs/academy` (Learner Lab: `LabRole`, API Gateway + ALB + S3) y `envs/aws` (cuenta propia: CloudFront, API Gateway, OIDC).
- El Learner Lab **bloquea CloudFront, AppSync y Cloud Map**: en Academy la entrada HTTPS es API Gateway, las partidas en vivo
  usan long polling (ADR-0002, enmienda 2026-09-29) y tournament/game llaman a users por el ALB (`/internal/*`).
- Región `us-east-1`. Perfiles: `chessquery-ro` (solo lectura, lo usa el MCP del agente),
  `chessquery-deploy` (despliegue, lo usa una persona o el CI), `chessquery-academy` (Learner Lab).
- El agente **no ejecuta** `terraform apply/destroy` ni comandos AWS que creen, modifiquen o borren recursos:
  propone el cambio (`terraform plan`) y lo aplica una persona o el pipeline.
- Configuración en SSM Parameter Store; nunca pedir, imprimir ni commitear credenciales.

<!-- BEGIN AWS Agent Toolkit rules -->
# AWS Guidance

- Where these AWS rules conflict with the project's own instructions, the
  project's instructions take precedence.
- Prefer the AWS MCP Server for AWS interactions — it provides sandboxed
  execution, observability, and audit logging. If unavailable, use the
  AWS CLI directly.
- Before starting a task, check whether a relevant AWS skill is available.
  Load the skill with `retrieve_skill` and prefer its guidance over
  general knowledge.
- When uncertain about specific AWS details (API parameters, permissions,
  limits, error codes), verify against documentation rather than guessing.
  State uncertainty explicitly if you cannot confirm.
- When creating infrastructure, prefer infrastructure-as-code (AWS CDK or
  CloudFormation) over direct CLI commands.
- When working with infrastructure, follow AWS Well-Architected Framework
  principles.
- Do not use em dashes in AWS resource names or descriptions. Use
  hyphens instead.

## Secret Safety

- MUST load the `aws-secrets-manager` skill first for any secret,
  credential, API key, token, or password task. MUST NOT call
  `secretsmanager get-secret-value` or `batch-get-secret-value`, and MUST
  NOT hit the Secrets Manager Agent daemon directly. MUST use
  `{{resolve:secretsmanager:secret-id:SecretString:json-key}}` with
  `asm-exec` so the secret resolves at runtime without entering context.
<!-- END AWS Agent Toolkit rules -->
