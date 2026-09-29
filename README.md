# ChessQuery

Plataforma de ajedrez competitivo para Chile: gestión digital de torneos presenciales para
clubes (SaaS) y partidas en vivo, perfil y progreso para jugadores (gratis).

Tercera iteración de la arquitectura: **Java (servicios) + Python (ETL) + React (web)**,
identidad delegada a **Microsoft Entra External ID** (Google federado), realtime propio por
WebSocket y despliegue cloud-native en AWS. Decisiones en `docs/adr/`.

## Estructura

```
libs/common         contrato de errores REST, envelope de eventos ChessEvents, idempotencia, EventBroadcaster
libs/auth-starter   resource server OIDC, @CurrentUser, resolución sub → playerId, X-Internal-Token
services/users      jugadores, identidad interna, catálogo, ratings e historial, ranking, organización (club) y roster, amistades
services/tournament torneos del club: inscripción, pareo suizo y round robin, desempates, cierre con rating, TRF, vista pública
services/game       partidas en línea: desafíos, jugadas validadas y reloj en el servidor, PGN, rating; en vivo por long polling
services/notifications (pendiente)
etl                 FIDE mensual y Federación (torneos, ficha pedida por el jugador) → S3 + SNS, listo para Lambda
infra/terraform     IaC: envs/academy (LabRole, API Gateway + ALB, ECS, Lambdas del ETL) y módulos; envs/aws pendiente
infra/events        topología única del bus (qué cola recibe qué evento), la leen LocalStack y Terraform
infra/localstack    topología local SNS/SQS/S3
apps/web            una sola app React (jugador y organizador) con login OIDC
packages/ui-lib     design system (dark, contraste AA validado en tests)
```

## API del servicio users (prefijos que el ALB enruta a `users`)

| Prefijo | Qué hay |
|---|---|
| `/api/users/**` | `GET /me`, `PUT /me/profile`, `GET /me/rating-history`, `POST /me/external-ratings/sync`, `GET /me/export`, `DELETE /me`, `GET /me/claim-suggestions`, `GET /{id}/public-profile`, `GET /{id}/rating-history`, `GET /search?q=`, `GET /ranking?type=&category=&region=` |
| `/api/public/**` | sin login, cache 5 min: `GET /ranking?type=NATIONAL\|FIDE_STANDARD\|FIDE_RAPID\|FIDE_BLITZ&category=&region=`, `GET /players/{id}` |
| `/api/organizations/**` | `POST /` (crear mi club = ser organizador), `GET/PUT /me`, roster: `GET/POST /me/roster`, `PATCH /me/roster/{id}/tags`, `DELETE /me/roster/{id}` |
| `/api/friends/**` | lista, solicitudes (`?direction=incoming|outgoing`), aceptar/rechazar, quitar, `GET /status/{otherId}` |
| `/api/catalog/**` | países y clubes federativos |
| `/internal/**` | solo servicio→servicio con `X-Internal-Token`: identidad por `sub`, provisión, resúmenes en lote, plan del organizador, `are-friends`, usernames para el ETL |

Eventos publicados y consumidos: ver `docs/events.md`.

## Desarrollo local

Auth local sin Entra: `make local-up` levanta un IdP de desarrollo (mock OIDC) en `http://localhost:8090/chessquery`.
Token para probar la API: `curl -X POST localhost:8090/chessquery/token -d grant_type=client_credentials -d client_id=<sub> -d client_secret=x -d scope=chessquery-api`
y correr los servicios con `OIDC_ISSUER_URI=http://localhost:8090/chessquery OIDC_AUDIENCE=chessquery-api`.
Desde la web (login en el navegador) el IdP simulado emite `aud=default`: en ese caso usar `OIDC_AUDIENCE=default`
(así lo hace `make e2e`).

Atajos: `make local-up` · `make users` · `make tournament` · `make game` · `make web` · `make etl-fide-local` ·
`make federation-worker` · `make test` · `make e2e` · `make image` · `make tf-check` · `make complexity` (ver `Makefile`).
Recorridos verificados en el navegador: `docs/verificacion/flujos-e2e.md`. Con `make etl-fide-local` la BD local queda con
los ~4.200 jugadores chilenos con rating FIDE: el ranking público (`/ranking`) muestra datos reales.

Requisitos: JDK 21, Maven 3.9, Docker, Node 20.

```bash
docker compose -f infra/docker-compose.yml up -d     # postgres, localstack (SNS/SQS/S3), mailpit
set -a; source infra/.env.example; set +a            # endpoint y credenciales dummy de LocalStack
export OIDC_ISSUER_URI=https://<tenant>.ciamlogin.com/<tenant-id>/v2.0
export OIDC_AUDIENCE=<client-id-de-la-api>
mvn -pl services/users spring-boot:run
curl -H "Authorization: Bearer <token>" localhost:8081/api/users/me

# Web (proxy de /api hacia el servicio local)
cp apps/web/.env.example apps/web/.env   # completar authority, client id y scope de Entra
npm install && npm run dev
```

Tests y cobertura (JaCoCo ≥ 90% por módulo, gate de CI):

```bash
mvn clean verify          # Java: usar siempre `clean`, los recursos de test viejos quedan en target/
npm run test -w web       # Vitest + axe-core + contraste AA
```

`PostgresSchemaTest` aplica Flyway contra PostgreSQL 16 en Testcontainers y arranca JPA con
`ddl-auto=validate`; es lo único que valida las migraciones. Se salta si no hay Docker.

## Convenciones

- Identidad: siempre desde `@CurrentUser UserPrincipal`, nunca del body. `ORGANIZER` = dueño de una organización.
- Errores REST: `{ status, error, message, timestamp }`. JSON en camelCase, columnas en snake_case.
- Eventos: envelope `{ eventId, eventType, timestamp, payload }` en el tópico SNS `chess-events`; una cola SQS dedicada por consumidor (filter policy por `eventType`, DLQ); catálogo en `docs/events.md`.
- Cada servicio es dueño de su schema PostgreSQL (`users`, `tournament`, `game`, `notifications`, `etl`); sin FKs entre schemas.
- `main` protegida; todo por PR con CI verde.
