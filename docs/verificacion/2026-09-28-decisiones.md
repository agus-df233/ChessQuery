# Verificación de decisiones — 28-09-2026

Cada decisión de `docs/adr/0001` y `0002` contrastada con evidencia verificable en el repo (archivo, test o
comando). Estados: ✅ verificada · 🟡 parcial · ⏳ pendiente · 🔁 ajustada hoy.

| # | Decisión | Evidencia | Estado |
|---|---|---|---|
| 1 | **IdaaS (Entra External ID) y cero contraseñas en la app** | `player` sin columna de contraseña (`V1__esquema_base.sql`); SPA con PKCE (`apps/web/src/auth/oidc.ts`); en local, IdP de desarrollo (`mock-oidc`) con JWT reales | ✅ (tenant de Entra aún sin crear) |
| 2 | **Servicios como resource servers, sin gateway propio** | `libs/auth-starter` (issuer + audiencia); `GET /api/users/me` sin token → 401 en el contenedor y en el benchmark | ✅ |
| 3 | **Identidad interna por `sub`** y roles de negocio en BD | `IdentityService` + `UsersIntegrationTest` (provisión JIT, organizador = dueño de club) | ✅ |
| 4 | **Un schema PostgreSQL por servicio**, Flyway dueño | `ddl-auto=validate`; migraciones V1–V2 aplicadas en Testcontainers | ✅ |
| 5 | **Bus SNS + SQS** en vez de RabbitMQ/Amazon MQ | `SnsSqsContractTest` y `UsersEventsIntegrationTest` contra LocalStack; módulo Terraform `messaging` | ✅ |
| 6 | **Consumidores idempotentes** (entrega al menos una vez) | `IdempotentConsumer` + `processed_event`; test de carrera en `EventsTest` | ✅ |
| 7 | **Privacidad por diseño (Ley 21.719)** | hash de RUT (`IdentifierHasher`, mismo valor en Java y Python: `IdentifierHasherContractTest`), año de nacimiento, menores abreviados (`PublicNames`), export/supresión, lista de supresión respetada por el consumer | ✅ (falta el flujo de consentimiento parental y los documentos legales) |
| 8 | **Match externo solo por identificadores** | fusión por nombre eliminada; `FederatedRatingSource` + caso de homónimos en `UsersIntegrationTest` | ✅ |
| 9 | **ETL FIDE** mensual | `etl/` probado con la lista real (9.271 CHI, 4.192 con rating, títulos idénticos a FIDE) | ✅ (falta programarlo como Lambda) |
| 10 | **Ingesta de la Federación** con validación y masivo apagado | `etl/chessquery_etl/federation/` (44 tests ETL, 99 %); contrato verificado en vivo contra el esquema real | ✅ código · ⏳ convenio para el masivo |
| 11 | **Terraform, un código y dos entornos** | `make tf-check` OK; `terraform plan` real contra el lab: 57 recursos sin errores | 🔁 Academy usa API Gateway en vez de CloudFront (enmienda ADR-0002) |
| 12 | **Entrada: API Gateway (cuenta propia) / ALB (Academy)** | Academy: HTTP API → ALB con cabecera secreta + S3 | 🔁 ajustada: CloudFront bloqueado en el lab |
| 13 | **Tiempo real con AppSync Events** | — | 🔁 en Academy: fallback STOMP (AppSync bloqueado); ⏳ servicio `game` |
| 14 | **Lambda solo para trabajos cortos** | ETL compatible con Lambda (`handler.lambda_handler`, `federation/cli.lambda_handler`) | 🟡 falta el módulo Terraform `jobs` |
| 15 | **Costo bajo** | sin NAT, Spot, SSM en vez de Secrets Manager (salvo la contraseña de RDS), apagado por Makefile | ✅ diseño · ⏳ medir con Cost Explorer tras el `apply` |
| 16 | **Rendimiento igual o mejor que la v2** | `docs/verificacion/benchmark-v2-vs-v3.md` | ✅ ranking y búsqueda ~2× · 🟡 perfil y memoria peores |
| 17 | **Código mantenible** | `make complexity`: ninguna función sobre CCN 10 (antes 12 funciones, la peor en 29) | ✅ |

## Hallazgos de hoy que cambiaron decisiones

1. El Learner Lab bloquea **CloudFront, AppSync y Cloud Map** → enmienda del ADR-0002 (entrada por API Gateway,
   STOMP como tiempo real, x86).
2. El consumer de ratings **fusionaba jugadores por nombre** → eliminado (riesgo de pegar el RUT de un homónimo).
3. `terraform plan` detectó un `for_each` que dependía de un valor conocido recién al aplicar → corregido antes
   del primer `apply`.
4. **LocalStack 2026.x exige token de licencia** → fijado en 4.14.0 (tests y entorno local).
5. El ranking solo ordenaba por ELO nacional, que depende del convenio con la Federación → agregado el ranking
   por rating FIDE (datos reales hoy).

## Qué falta para cerrar

- `apply` en el lab (lo ejecuta una persona: `make academy-bootstrap`, `academy-apply`, `academy-image`, `academy-web`).
- Tenant de Entra y redirect URI con el `app_url`.
- Módulo `jobs` (Lambdas del ETL + EventBridge Scheduler) y `envs/aws` (cuenta propia).
- Servicios `tournament`, `game`, `notifications`; consentimiento parental; documentos de privacidad.
