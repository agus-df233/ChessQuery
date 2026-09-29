# ChessQuery

Plataforma de ajedrez competitivo para Chile: gestión digital de torneos presenciales para clubes (SaaS) y
partidas en línea, perfil y progreso para jugadores (gratis).

Tercera iteración de la arquitectura: **Java 21 / Spring Boot 3.5 (servicios) + Python (ETL) + React (web)**, identidad
delegada a **Microsoft Entra External ID** (con Google; la app no guarda contraseñas) y despliegue en AWS con
Terraform. Decisiones en `docs/adr/`. ¿Vas a trabajar en el repo? Lee **`CONTRIBUTING.md`**.

## Empezar en 5 minutos

Requisitos: **JDK 21, Maven 3.9, Docker, Node 20, Python 3.12** y [`uv`](https://docs.astral.sh/uv/)
(Terraform 1.10+ solo si tocas infraestructura).

```bash
npm install
make dev          # la app completa en http://localhost:5173 · Ctrl+C apaga todo
```

`make dev` levanta Postgres, LocalStack (SNS/SQS/S3), un IdP simulado que hace de Entra, los servicios `users`,
`tournament` y `game`, el worker del ETL, una Federación falsa con datos ficticios y la web. Para entrar: **"Entrar
con mi correo"**, cualquier usuario y los claims que imprime la consola. No necesita tenant de Entra ni acceso a
AWS. Los logs quedan en `.logs/`.

## Qué hay en cada carpeta

```
libs/common         errores REST, sobre de eventos ChessEvent, idempotencia, cálculo ELO, lectura de payloads
libs/auth-starter   resource server OIDC, @CurrentUser, resolución sub → playerId, cliente interno hacia users
services/users      jugadores e identidad, catálogo, ratings e historial, ranking, club y roster, amistades, privacidad
services/tournament torneos del club: inscripción, pareo suizo y round robin, desempates, cierre con rating, TRF
services/game       partidas en línea: desafíos, jugadas y reloj en el servidor, PGN, rating; en vivo por long polling
etl                 FIDE mensual y Federación (torneos, ficha pedida por el jugador) → S3 + eventos; Lambdas en la nube
apps/web            una sola app React para jugador y organizador (+ pruebas E2E en apps/web/e2e)
packages/ui-lib     design system (tema oscuro, contraste AA probado)
infra/terraform     IaC: envs/academy (Learner Lab) y módulos; envs/aws (cuenta propia) pendiente
infra/events        topología única del bus: qué cola recibe qué evento (la leen LocalStack y Terraform)
infra/localstack    arranque de LocalStack a partir de infra/events
scripts             make dev / make e2e (stack local) y benchmark v2 vs v3
docs                ADR, catálogo de eventos, guías del ETL, verificaciones (ver docs/README.md)
```

`services/notifications` está pendiente.

## APIs (el ALB y el proxy de Vite enrutan por prefijo)

| Servicio | Prefijos | Qué hay |
|---|---|---|
| `users` :8081 | `/api/users/**` | yo (`/me`), perfil, historial de rating, exportar/borrar mis datos, vincular ficha federativa, reclamar ficha ("¿Eres tú?"), búsqueda, ranking |
| | `/api/organizations/**` | crear mi club (= ser organizador), datos del club, roster |
| | `/api/friends/**`, `/api/catalog/**` | amistades; países y clubes federativos |
| | `/api/public/**` | sin login: ranking y perfil público (menores con apellido abreviado) |
| `tournament` :8082 | `/api/tournaments/**` | mis torneos, crear/editar, inscribir, rondas, resultados por mesa, cerrar, exportar TRF |
| | `/api/public/tournaments/**` | sin login: listado, detalle, rondas, tabla y calendario de la Federación |
| `game` :8083 | `/api/games/**` | mis partidas, desafiar, aceptar/rechazar, jugar, abandonar, tablas; `?afterVersion=n` espera cambios (long polling) |
| | `/api/public/games/**` | sin login: ver una partida y descargar su PGN |
| `users` | `/internal/**` | solo servicio→servicio con `X-Internal-Token` (nunca expuesto por API Gateway) |

Errores: `{ status, error, message, timestamp }`. Eventos entre servicios: `docs/events.md`.

## Comandos

| Comando | Qué hace |
|---|---|
| `make dev` | app completa en local (ver arriba) |
| `make test` | todas las pruebas: Java (`mvn clean verify`, cobertura ≥ 90 %), ETL (pytest), web (Vitest + axe) |
| `make e2e` | recorridos del jugador y del organizador en Chromium (Playwright) contra el stack local |
| `make complexity` | complejidad ciclomática ≤ 10 por función |
| `make tf-check` | `terraform fmt` + `validate` |
| `make local-up` / `make users` / `make tournament` / `make game` / `make web` | piezas sueltas (ver `Makefile`) |
| `make etl-fide-local` | carga la lista FIDE real (≈ 4.200 jugadores chilenos) en tu entorno local |

Piezas sueltas con el IdP simulado: `OIDC_ISSUER_URI=http://localhost:8090/chessquery` y `OIDC_AUDIENCE=default`
(login desde la web) o `chessquery-api` (token por `client_credentials` para probar la API con curl).

## Convenciones (resumen; detalle en `CONTRIBUTING.md` y `CLAUDE.md`)

- Identidad siempre desde `@CurrentUser UserPrincipal`, nunca del body ni de la URL.
- Datos personales (Ley 21.719): a terceros nunca RUT, email, fecha de nacimiento ni género; menores abreviados.
- Un schema PostgreSQL por servicio, sin FKs entre schemas; Flyway es dueño del esquema.
- Eventos: sobre `ChessEvent` en el tópico SNS `chess-events`, una cola SQS por consumidor; todo evento nuevo se
  documenta primero en `docs/events.md`.
- Ramas: se trabaja en `develop` y ramas de feature, todo por PR con CI verde; `main` recibe solo desde `develop`.
