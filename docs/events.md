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
- La topología es infraestructura: `infra/localstack/init/ready.d/10-chess-events.sh` (local) y el
  módulo Terraform `messaging` (nube). Los servicios solo publican (`EventPublisher`) y consumen
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
| `player.deleted` | users | tournament, game, notifications | `{ playerId }` — el titular ejerció supresión: la fila quedó anonimizada (el id se conserva por integridad); cada servicio borra o anonimiza lo suyo | ✅ productor |
| `subscription.changed` | users | notifications | `{ organizationId, ownerId, oldPlan, newPlan, status, gateway, reason }` | paso 6 |
| `rating.updated` | etl | users (`users-rating`) | `{ source: FIDE\|FEDERACION\|LICHESS\|CHESSCOM, period?, players: [ { firstName, lastName, federationId?, fideId?, title?, rutHash?, birthYear?, clubName?, sourceUrl?, period?, eloNational?, eloFideStandard?, eloFideRapid?, eloFideBlitz? } \| { lichessUsername, eloLichess* } \| { chesscomUsername, eloChesscom* } ] }` — el RUT viaja **solo como `rutHash`** (HMAC con el pepper compartido); de la fecha de nacimiento, solo el año | ✅ (FIDE y Federación) |
| `federation.tournament.published` | etl | tournament (paso 2) | `{ source: FEDERACION, tournaments: [ { federationTournamentId, title, city?, region?, clubName?, startDate, endDate, type?, rounds?, timeControl?, category?, ratedNational, ratedFide } ] }` — solo datos del evento, sin participantes (requieren convenio); se publican los nuevos o modificados | productor ✅ / consumer paso 2 |
| `elo.updated` | game | users (`users-elo`) | `{ playerId, oldElo, newElo, delta, ratingType, gameId }` — uno por jugador | consumer ✅ / productor paso 3 |
| `game.finished` | game | notifications | `{ gameId, whitePlayerId, blackPlayerId, result, ... }` | paso 3 |
| `tournament.round.generated` | tournament | game, notifications | `{ tournamentId, round, pairings: [...] }` | paso 2 |
| `friend.request.created` | users | notifications | `{ requestId, fromPlayerId, fromName, toPlayerId }` | ✅ |
| `friend.request.accepted` | users | notifications | `{ requestId, fromPlayerId, toPlayerId, acceptedName }` | ✅ |

Los payloads exactos se completan al portar cada servicio, a partir de `docs/events.md` de la v2.
