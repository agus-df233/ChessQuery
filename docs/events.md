# Catálogo de eventos — tópico SNS `chess-events` + una cola SQS por consumidor

Fuente de verdad. Todo evento nuevo se documenta aquí **antes** de codificar productor y consumidor.
Decisión de transporte: `docs/adr/0002-despliegue-aws-bajo-costo.md` (reemplaza al exchange RabbitMQ).

Envelope (`cl.chessquery.common.events.ChessEvent`), publicado como cuerpo JSON del mensaje SNS:

```json
{ "eventId": "uuid-v4", "eventType": "game.finished", "timestamp": "2026-09-16T21:00:00Z", "payload": { } }
```

El mensaje lleva además el **atributo SNS `eventType`** (= routing key), que es lo que filtran las
suscripciones.

Reglas:
- Una cola SQS **dedicada por servicio consumidor y flujo** (`<servicio>-<dominio>`, p. ej. `users-elo`),
  suscrita al tópico con **filter policy** `{"eventType": [...]}` y **raw message delivery** (la cola
  recibe el envelope tal cual). Nunca compartir colas.
- Cada cola tiene su **DLQ** `<cola>-dlq` con `maxReceiveCount = 5`; un mensaje en una DLQ dispara alarma.
- La topología (qué cola recibe qué eventos) vive en **un solo archivo**, `infra/events/topology.json`,
  que leen tanto `infra/localstack/init/ready.d/10-chess-events.sh` (local) como el módulo Terraform
  `messaging` (nube). Los servicios solo publican (`EventPublisher`) y consumen
  (`@SqsListener("${chessquery.events.queues.<flujo>}")`).
- SQS entrega **al menos una vez** y sin orden global: consumidores idempotentes con `IdempotentConsumer`
  + tabla `processed_event` en el schema del servicio:

```sql
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
```

| Routing key | Productor | Consumidores | Payload | Estado |
|---|---|---|---|---|
| `player.provisioned` | users | notifications | `{ playerId, email, fullName }` — primer acceso de una cuenta nueva | ✅ |
| `player.claimed` | users | tournament, notifications | `{ playerId, email, fullName, organizerId }` — un provisorio del roster reclamado por su dueño (mismo `playerId`, sin re-vinculación) | ✅ |
| `player.provisional.created` | users | notifications | `{ playerId, organizerId, email }` | ✅ |
| `player.updated` | users | — | `{ playerId, fields: [...] }` | ✅ |
| `player.merged` | users | tournament (`tournament-players`) | `{ fromPlayerId, intoPlayerId }` — el titular reclamó una ficha federada sin dueño tras verificar su identidad (RUT o año + nombre): ids federativos, ratings, historial y títulos pasan a su cuenta y `fromPlayerId` queda inactivo; cada servicio reasigna sus referencias | ✅ productor |
| `federation.lookup.requested` | users | etl (`etl-federation-lookup`) | `{ playerId, federationId }` — el jugador vinculó su id federativo (consentimiento): el ETL consulta **solo esa ficha** y responde con `rating.updated` (source `FEDERACION`) | ✅ |
| `player.deleted` | users | tournament, game, notifications | `{ playerId }` — el titular ejerció supresión: la fila quedó anonimizada (el id se conserva por integridad); cada servicio borra o anonimiza lo suyo | ✅ productor |
| `subscription.changed` | users | notifications | `{ organizationId, ownerId, oldPlan, newPlan, status, gateway, reason }` | paso 6 |
| `rating.updated` | etl | users (`users-rating`) | `{ source: FIDE\|FEDERACION\|LICHESS\|CHESSCOM, period?, players: [ { firstName, lastName, federationId?, fideId?, title?, rutHash?, birthYear?, clubName?, sourceUrl?, period?, eloNational?, eloFideStandard?, eloFideRapid?, eloFideBlitz? } \| { lichessUsername, eloLichess* } \| { chesscomUsername, eloChesscom* } ] }` — el RUT viaja **solo como `rutHash`** (HMAC con el pepper compartido); de la fecha de nacimiento, solo el año | ✅ (FIDE y Federación) |
| `federation.tournament.published` | etl | tournament (`tournament-federation`) | `{ source: FEDERACION, tournaments: [ { federationTournamentId, title, city?, region?, clubName?, startDate, endDate, type?, rounds?, timeControl?, category?, ratedNational, ratedFide } ] }` — solo datos del evento, sin participantes (requieren convenio); se publican los nuevos o modificados | ✅ |
| `elo.updated` | game, tournament | users (`users-elo`) | `{ playerId, oldElo, newElo, delta, ratingType, source?: GAME\|TOURNAMENT, gameId?, tournamentId? }` — uno por jugador; game lo emite al terminar una partida por rating con al menos una jugada por lado; tournament lo emite al cerrar un torneo válido para rating (ELO sobre el rating previo al torneo, `ratingType = PLATFORM`) | ✅ (game y tournament) |
| `game.finished` | game | notifications (futuro) | `{ gameId, whitePlayerId, blackPlayerId, result: WHITE_WINS\|BLACK_WINS\|DRAW, termination, rated }` — el PGN queda en `GET /api/public/games/{id}/pgn` | ✅ productor |
| `tournament.round.generated` | tournament | notifications (futuro) | `{ tournamentId, round, pairings: [ { board, whitePlayerId, blackPlayerId? } ] }` — `blackPlayerId` nulo = bye | ✅ productor |
| `tournament.finished` | tournament | notifications (futuro) | `{ tournamentId, name, standings: [ { playerId, position, points } ] }` | ✅ productor |
| `friend.request.created` | users | notifications | `{ requestId, fromPlayerId, fromName, toPlayerId }` | ✅ |
| `friend.request.accepted` | users | notifications | `{ requestId, fromPlayerId, toPlayerId, acceptedName }` | ✅ |

Los payloads exactos se completan al portar cada servicio, a partir de `docs/events.md` de la v2.
