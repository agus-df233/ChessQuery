# Catálogo de eventos — exchange `ChessEvents` (topic)

Fuente de verdad. Todo evento nuevo se documenta aquí **antes** de codificar productor y consumidor.

Envelope (`cl.chessquery.common.events.ChessEvent`):

```json
{ "eventId": "uuid-v4", "eventType": "game.finished", "timestamp": "2026-09-16T21:00:00Z", "payload": { } }
```

Reglas:
- Una cola durable **dedicada por servicio consumidor** (`<servicio>.<dominio>.queue`); nunca compartir colas.
- Consumidores idempotentes con `IdempotentConsumer` + tabla `processed_event` en el schema del servicio:

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
| `subscription.changed` | users | notifications | `{ organizationId, ownerId, oldPlan, newPlan, status, gateway, reason }` | paso 6 |
| `rating.updated` | etl | users (`users.rating.queue`) | `{ source: AJEFECH\|LICHESS\|CHESSCOM, players: [ { firstName, lastName, federationId?, fideId?, rut?, birthDate?, clubName?, eloNational?, eloFideStandard? } \| { lichessUsername, eloLichess* } \| { chesscomUsername, eloChesscom* } ] }` | consumer ✅ / productor paso 5 |
| `elo.updated` | game | users (`users.elo.queue`) | `{ playerId, oldElo, newElo, delta, ratingType, gameId }` — uno por jugador | consumer ✅ / productor paso 3 |
| `game.finished` | game | notifications | `{ gameId, whitePlayerId, blackPlayerId, result, ... }` | paso 3 |
| `tournament.round.generated` | tournament | game, notifications | `{ tournamentId, round, pairings: [...] }` | paso 2 |
| `friend.request.created` | users | notifications | `{ requestId, fromPlayerId, fromName, toPlayerId }` | ✅ |
| `friend.request.accepted` | users | notifications | `{ requestId, fromPlayerId, toPlayerId, acceptedName }` | ✅ |

Los payloads exactos se completan al portar cada servicio, a partir de `docs/events.md` de la v2.
