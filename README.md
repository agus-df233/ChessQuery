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
services/users      jugadores, identidad interna, organizaciones (clubes), planes, billing, amigos, ratings
services/tournament (paso 2)   services/game (paso 3)   services/notifications (paso 4)
etl/                (paso 5)   apps/web (paso 1)        infra/terraform (paralelo)
```

## Desarrollo local

Requisitos: JDK 21, Maven 3.9, Docker, Node 20.

```bash
docker compose -f infra/docker-compose.yml up -d     # postgres, rabbitmq (+stomp), minio, mailpit
export OIDC_ISSUER_URI=https://<tenant>.ciamlogin.com/<tenant-id>/v2.0
export OIDC_AUDIENCE=<client-id-de-la-api>
mvn -pl services/users spring-boot:run
curl -H "Authorization: Bearer <token>" localhost:8081/api/users/me
```

Tests y cobertura (JaCoCo ≥ 90% por módulo, gate de CI):

```bash
mvn clean verify
```

`PostgresSchemaTest` aplica Flyway contra PostgreSQL 16 en Testcontainers y arranca JPA con
`ddl-auto=validate`; es lo único que valida las migraciones. Se salta si no hay Docker.

## Convenciones

- Identidad: siempre desde `@CurrentUser UserPrincipal`, nunca del body. `ORGANIZER` = dueño de una organización.
- Errores REST: `{ status, error, message, timestamp }`. JSON en camelCase, columnas en snake_case.
- Eventos: envelope `{ eventId, eventType, timestamp, payload }` en el exchange `ChessEvents`; catálogo en `docs/events.md`; una cola dedicada por servicio consumidor.
- Cada servicio es dueño de su schema PostgreSQL (`users`, `tournament`, `game`, `notifications`, `etl`); sin FKs entre schemas.
- `main` protegida; todo por PR con CI verde.
