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
| `player.provisioned` | users | notifications | `{ playerId, email }` | paso 1 |
| `player.claimed` | users | tournament, notifications | `{ playerId, organizerId, email }` | paso 1 |
| `subscription.changed` | users | notifications | `{ organizationId, ownerId, oldPlan, newPlan, status, gateway, reason }` | paso 6 |
| `rating.updated` | etl | users | `{ source, players: [...] }` | paso 5 |
| `elo.updated` | game | users | `{ playerId, oldElo, newElo, delta, ratingType, gameId }` | paso 3 |
| `game.finished` | game | notifications | `{ gameId, whitePlayerId, blackPlayerId, result, ... }` | paso 3 |
| `tournament.round.generated` | tournament | game, notifications | `{ tournamentId, round, pairings: [...] }` | paso 2 |
| `friend.requested` / `friend.accepted` | users | notifications | `{ fromPlayerId, toPlayerId }` | paso 1 |

Los payloads exactos se completan al portar cada servicio, a partir de `docs/events.md` de la v2.
