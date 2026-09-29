# Receta: agregar una fuente nueva al ETL

Ejemplo: sincronizar ratings de otra plataforma o una federación regional. Seguir los pasos en orden; cada uno
tiene su verificación.

## 1. Entender la fuente (antes de escribir código)

- ¿Es oficial, tiene términos de uso? ¿Qué **base legal** tenemos para tratar esos datos (fuente pública,
  consentimiento del titular, convenio)? Si hay datos de personas, revisarlo con el equipo antes de seguir.
- ¿Qué campos trae? Arma la **lista blanca**: solo lo necesario para el producto.
- ¿Qué identificador estable tiene cada registro? (id propio, FIDE id, RUT). **Nunca** el nombre.

## 2. Contrato del evento

Si la fuente produce ratings, se reutiliza `rating.updated` con un `source` nuevo. Si produce otra cosa, se define
un evento nuevo. En ambos casos: **documentar primero en `docs/events.md`** y coordinar con el consumidor.

## 3. Parser o cliente

- Archivo propio en `etl/chessquery_etl/` (o un subpaquete si la fuente es grande, como `federation/`).
- Leer por nombre de columna/campo, no por posición fija.
- Un cliente HTTP a un sitio de terceros siempre con ritmo máximo, reintentos y circuit breaker (reutilizar el
  patrón de `federation/client.py`).

## 4. Validación y minimización

- Reglas chicas, una por función, que devuelvan el motivo del rechazo (ver `federation/validation.py`).
- Minimizar antes de publicar: año en vez de fecha, `rutHash` en vez de RUT (`federation/privacy.py`).
- Umbral de rechazos: si se supera, la corrida no publica.

## 5. Orquestación con el pipeline común

Reutilizar `pipeline.stage`, `pipeline.changed_rows`, `pipeline.publish_batches` y `pipeline.write_manifest`
(ver `federation/ingest.py`). No reimplementar el diff ni el envelope.

## 6. Tests (cobertura ≥ 90 %)

- Fixtures **sintéticas** con la forma real de la fuente (nunca datos de personas reales).
- Probar: parser, cada regla de validación, minimización (que el dato sensible no salga), diff (segunda corrida
  sin cambios publica 0), umbral de rechazos y el cliente con transporte falso.

## 7. Lado consumidor (`services/users`)

Si es una fuente de ratings nueva: una implementación de `RatingSource` que declare su `source` en `sources()`
(ver `FederatedRatingSource` y `LinkedAccountRatingSource`). El consumer no se toca.

## 8. Operación

- Flag para encender/apagar la fuente y variables documentadas en una tabla (como `docs/etl/federacion.md`).
- Entrada en el runbook con los errores esperables.
- Lambda + EventBridge Scheduler en el módulo Terraform `jobs`.

## Checklist de privacidad

- [ ] Base legal identificada (fuente pública, consentimiento o convenio).
- [ ] Lista blanca de campos; campos prohibidos listados explícitamente.
- [ ] RUT → `rutHash`; fecha de nacimiento → año; sin emails ni datos de pago.
- [ ] `rejected/` y logs sin datos personales.
- [ ] La supresión (`data_suppression`) se respeta del lado de `users`.
