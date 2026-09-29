# Instrucciones para agentes que trabajan en `etl/`

Contexto completo: `docs/etl/onboarding.md`, `docs/etl/federacion.md`, `docs/etl/nueva-fuente.md`,
`docs/etl/glosario.md` y el contrato de eventos en `docs/events.md`. Reglas generales del repo: `CLAUDE.md` en la raíz.

## Reglas (no romper sin acuerdo explícito del equipo)

1. **Contrato de eventos**: no cambiar la forma de `rating.updated` ni de `federation.tournament.published` sin
   actualizar antes `docs/events.md` y avisar a quien mantiene `services/users`.
2. **Lista blanca de campos** (`chessquery_etl/federation/contract.py`): no agregar campos de personas sin revisión
   de privacidad. Nunca pedir `email`, `canon*` (pagos) ni flags de administración.
3. **RUT**: nunca sale del proceso en claro. Validar DV y publicar solo `rutHash` (`federation/privacy.py`). No cambiar
   el cálculo del hash: debe coincidir con `IdentifierHasher.java` (test `test_hash_coincide_con_java`).
4. **Fecha de nacimiento**: publicar solo el año.
5. **Descarga masiva de personas**: `FEDERATION_BULK_PLAYERS_ENABLED` queda en `false` por defecto; no activarlo en
   código, CI ni infraestructura.
6. **Datos de prueba**: siempre sintéticos (nombres y RUT ficticios). Nunca commitear datos reales ni imprimirlos en logs.
7. **Sitios de terceros**: todo cliente HTTP con ritmo máximo, reintentos con backoff y circuit breaker.
8. **Rechazos y logs**: solo id + motivo, sin datos personales.

## Cómo trabajar

- Reutilizar el esqueleto de `chessquery_etl/pipeline.py` (`stage`, `changed_rows`, `publish_batches`,
  `write_manifest`, `envelope`); no reimplementar diff ni envelope.
- Funciones chicas: complejidad ciclomática ≤ 10 (`make complexity`).
- Comentarios, docstrings y mensajes en español latino, explicando el *por qué*.
- Tests: `make test-etl` (pytest, cobertura ≥ 90 %). Sin red en los tests: usar `tests/federation_fakes.py`.
- Commits cortos, en español y con foco funcional, sin trailers de co-autoría.
