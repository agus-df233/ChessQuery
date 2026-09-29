# Ingesta de la Federación Chilena de Ajedrez

Código: `etl/chessquery_etl/federation/`. Fuente: el endpoint GraphQL público del sitio
(`https://www.federacionchilenadeajedrez.cl/graphql`), el mismo que usa su página de jugadores. **No es una API
oficial**: no tiene documentación ni términos de uso publicados, y su esquema puede cambiar sin aviso.

## Qué se trae y cómo

| Qué | Query GraphQL | Modo | Estado |
|---|---|---|---|
| Torneos publicados (datos del evento) | `rankedTournaments` / `tournaments(word)` | en vivo | ✅ encendido |
| Un jugador por su id federativo | `person(id)` | consulta puntual, cuando la persona vincula su ficha | ✅ encendido |
| Todos los jugadores por región y género | `personRanking(id, gender, elo, region)` | masivo | ⛔ apagado hasta convenio |
| Historial de ELO por período | `periodPlayer` / `elosHistoric` | — | ⏳ semántica por confirmar con la Federación |

**Por qué el masivo está apagado:** la ficha de cada persona expone RUT, fecha de nacimiento, email y estado de pago
de cuotas, incluidos menores. Sin convenio no hay una base legal clara para descargar miles de fichas (Ley 19.628 /
21.719). La consulta puntual sí tiene base: el propio titular la pide al vincular su id. Además, los argumentos de
`personRanking` (`id`, `elo`) no están documentados: `FEDERATION_RANKING_ID` y `FEDERATION_RANKING_ELO` se completan
cuando la Federación confirme su significado.

## Consulta puntual: el jugador vincula su ficha

Es la única vía por la que hoy entran datos personales de la Federación, y siempre a pedido del titular:

1. En "Mi inicio", el jugador escribe su id federativo → `users` lo guarda en su cuenta y publica
   `federation.lookup.requested { playerId, federationId }`.
2. La cola `etl-federation-lookup` lo recibe. En la nube la atiende la Lambda `federation-lookup` (disparada por
   SQS); en local, `make federation-worker` (`federation/worker.py`, long polling).
3. Se corre el modo `lookup` para **solo ese id** (`person(id)`) → validación → minimización (RUT → `rutHash`,
   fecha → año) → `rating.updated` (source `FEDERACION`).
4. `users` encuentra la cuenta por el id federativo y completa ELO nacional, club y año de nacimiento.

Si la consulta falla, el mensaje vuelve a la cola (`batchItemFailures`) y tras 5 intentos pasa a la DLQ
`etl-federation-lookup-dlq` (alarma). Un mensaje mal formado se descarta con un log: reintentarlo no lo arregla.
Si la ficha ya existía en ChessQuery sin dueño, el jugador la **reclama** ("¿Eres tú?") verificando su RUT; eso lo
resuelve `users` (evento `player.merged`), no el ETL.

## Parámetros (variables de entorno)

| Variable | Por defecto | Efecto |
|---|---|---|
| `FEDERATION_BASE_URL` | `https://www.federacionchilenadeajedrez.cl` | Sitio de origen (cambiar para pruebas contra un doble) |
| `FEDERATION_RPS` | `1` | Requests por segundo (máximo 5: es un sitio de terceros) |
| `FEDERATION_TIMEOUT_S` | `30` | Timeout por request |
| `FEDERATION_MAX_RETRIES` | `3` | Reintentos ante 429/5xx/red, con espera 1 s, 2 s, 4 s + jitter |
| `FEDERATION_BREAKER_THRESHOLD` | `5` | Fallos seguidos que abren el circuito y cortan la corrida |
| `FEDERATION_USER_AGENT` | `ChessQuery-ETL/3 (+…; contacto@…)` | Identificación con contacto (cortesía con el sitio) |
| `FEDERATION_BULK_PLAYERS_ENABLED` | `false` | Descarga masiva de personas. **Solo con convenio firmado** |
| `FEDERATION_TOURNAMENTS_ENABLED` | `true` | Ingesta de torneos |
| `FEDERATION_REGIONS` | `all` | Ids de región separados por coma, o `all` |
| `FEDERATION_RANKING_ID` / `FEDERATION_RANKING_ELO` | vacío | Argumentos de `personRanking` (por confirmar) |
| `FEDERATION_MAX_REJECTED_RATIO` | `0.05` | Si se rechaza más de esta proporción, la corrida no publica |
| `PRIVACY_PEPPER` (local) / `PRIVACY_PEPPER_PARAM` (nube, SSM) | — | Secreto del hash del RUT; **debe ser el mismo que usa `users`** |
| `ETL_BUCKET`, `CHESS_EVENTS_TOPIC_ARN` | local: `chessquery-etl`, tópico de LocalStack | Destinos S3 y SNS |

## Campos: qué pedimos y qué jamás

Lista blanca (`contract.py`), por tipo:
- **Persona:** id, firstName, secondName, lastName, lastNameSecond, gender, title, fideIdentificator, eloNat,
  eloInter, birthdayFormated, identificator (RUT), clubBasic (id, nombre, región).
- **Torneo:** id, title, city, startDate, endDate, type, rounds, timeControl, category, eloN, eloInt, club.

Nunca se piden (aunque existan): `email`, `username`, `canon*` (pagos de cuota), `requestedChangeClub`, flags de
administración (`PERSON_FORBIDDEN` en `contract.py`).

**Minimización antes de publicar** (`players.to_event_player`): de la fecha de nacimiento solo queda el **año**; el RUT
se valida (dígito verificador) y se publica como **`rutHash`** (HMAC-SHA256 con el pepper). El RUT en claro no
llega ni a S3 ni a SNS.

## Validaciones (motivos de rechazo)

| Motivo | Regla |
|---|---|
| `id_invalido` | el id no es numérico |
| `nombre_vacio` | falta nombre o apellido |
| `rut_invalido` | tiene RUT pero el dígito verificador no calza |
| `elo_fuera_de_rango` | ELO distinto de 0 y fuera de 800–3000, o no numérico |
| `titulo_desconocido` | título fuera de GM, IM, FM, CM, WGM, WIM, WFM, WCM |
| `duplicado` | el mismo id aparece dos veces en la corrida |
| `torneo_sin_titulo`, `fecha_invalida`, `fin_antes_de_inicio`, `rondas_fuera_de_rango` | reglas de torneos |

Los rechazos se guardan en `rejected/…jsonl` **solo con id y motivo**. Además, antes de bajar datos se verifica el
**contrato**: si falta un campo de la lista blanca en el esquema, la corrida falla sin descargar nada.

## Salidas en S3

```
staged/source=federation-players/mode={lookup|bulk}/period=AAAA-MM/players.jsonl
staged/source=federation-tournaments/latest.jsonl         ← base del diff
staged/source=federation-tournaments/date=AAAA-MM-DD.jsonl
rejected/…jsonl                                            ← id + motivo
manifests/…json                                            ← read, accepted, rejected, changed, batches
```

## Runbook (qué hacer cuando falla)

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| `ContractError: Campos que ya no existen…` | la Federación cambió su esquema | `make federation-contract`, comparar con `contract.py`, ajustar la query y la lista blanca (con revisión de privacidad) |
| `CircuitOpenError` | el sitio respondió mal varias veces seguidas | esperar y reintentar; revisar si el sitio está caído; no subir `FEDERATION_RPS` |
| `RejectionThresholdError` (> 5 %) | cambió el formato de un campo | revisar `rejected/` (motivos agregados), ajustar la regla o el parser; no subir el umbral sin entender la causa |
| `HTTP 4xx` sin reintento | consulta inválida o bloqueo | revisar la query; contactar a la Federación si es un bloqueo |
| `BulkDisabledError` | se pidió el masivo con el flag apagado | es lo esperado sin convenio |
