# Plan de pruebas de ChessQuery v3

Vigente al 08-10-2026. Qué se prueba, con qué herramienta, dónde está y cómo se corre. Los recorridos de punta a
punta paso a paso están en `flujos-e2e.md`; las decisiones de herramientas, en `docs/adr/0002-despliegue-aws.md`.

**Regla general.** Todo corre en local o en el CI, con datos **sintéticos**. Las pruebas ofensivas se hacen **solo
contra el stack local**; contra la nube, como mucho una revisión pasiva. Ninguna prueba toca la Federación, la FIDE,
Lichess ni Chess.com reales: se usan dobles con el mismo contrato.

```bash
make test         # Java (mvn clean verify + JaCoCo ≥ 90 % por módulo) + ETL (pytest ≥ 90 %) + web (vitest + axe)
make e2e          # Playwright: 10 recorridos (incluida la suite de seguridad) contra el stack local completo
make complexity   # CCN ≤ 10 por función (lizard)
make tf-check     # terraform fmt + validate
```

## 1. Caja blanca (el código por dentro)

| Qué | Dónde | Cómo |
|---|---|---|
| Dominio con ramas y casos límite: pareo suizo y round robin, desempates, ELO por ritmo y categoría del ritmo (cortes 180/480/1500 s), reloj, inscripción con cupo y lista de espera, escape del TRF | `services/*/src/test`, `libs/common/src/test` | JUnit 5 + AssertJ |
| Persistencia y flujos reales con PostgreSQL | `*IntegrationTest` (Testcontainers, Postgres 16) | carrera del primer ingreso, roster masivo, inscripción, sala en vivo por versión, salas de juego |
| Reglas puras de la web (CSV del roster, ritmos, seguimiento, bienvenida) | `apps/web/src/lib/*.spec.ts`, `welcome.a11y.spec.tsx` | Vitest |
| ETL: parsers, contrato, privacidad (hash igual al de Java), clientes HTTP con ritmo, reintentos y corte | `etl/tests` | pytest con transporte simulado |
| **Cobertura** | JaCoCo y pytest-cov | gate del CI: ≥ 90 % por módulo |
| **Complejidad** | `make complexity` | CCN ≤ 10 y ≤ 100 líneas por función |

## 2. Caja negra funcional (sin mirar el código)

**E2E por historia** (Playwright, `apps/web/e2e/`), con particiones de equivalencia y valores límite:

| Historia | Archivo | Casos límite incluidos |
|---|---|---|
| 1 · Nuevo jugador | `jugador.spec.ts`, `desafio-abierto.spec.ts`, `respaldo.spec.ts` | bienvenida completa; Lichess/Chess.com por Lambda; desafío con el ritmo favorito; sin WebSocket sigue por long polling; enlace abierto sin sesión |
| 2 · Organizador | `organizador.spec.ts`, `torneo-completo.spec.ts`, `roster-invitacion.spec.ts` | CSV con filas inválidas y duplicadas; cupo lleno y lista de espera; aprobación; acreditación repetida; no presentados; retiro a mitad de torneo; invitación usada una vez |
| 3 · Sala en vivo | `sala-apoderado.spec.ts` | dos dispositivos (monitor y celular); se actualiza sin recargar |
| 4 · Clase | `sala.spec.ts` | cupo de la sala; espectadores; cambiar la cantidad de tableros con partidas en curso |
| Todas las vistas | `vistas.spec.ts` | axe WCAG 2 A/AA y sin scroll horizontal en 375 px |

**Contrato de la API:**

- códigos HTTP y formato de error `{status, error, message, timestamp}` en las pruebas de integración;
- 404 y 405 en `seguridad.spec.ts`.

**Frontend:**

- Vitest + Testing Library + **axe** en cada página. El asistente de bienvenida se revisa en cada paso.
- Valores límite de los usernames (2 y 30 caracteres permitidos; 1 y 31, no) y del id federativo (hasta 10 dígitos).

## 3. Seguridad ofensiva (pentesting web, solo contra el stack local)

**Suite `apps/web/e2e/seguridad.spec.ts`:** corre en cada `make e2e`. Detalle en `flujos-e2e.md`.

| Ataque | Resultado esperado |
|---|---|
| JWT: sin token, `alg:none`, sujeto cambiado, firma alterada, audiencia o emisor ajenos | 401 |
| IDOR y escalamiento (torneo, ronda, cierre, inscritos y TRF ajenos; tableros de otra sala; roster ajeno; jugar por otro) | 403/404 sin filtrar datos |
| Adivinar tokens de invitación, desafío abierto y acreditación | 404 uniforme; tokens de 128 bits |
| Datos personales en respuestas públicas (Ley 21.719) | sin RUT, `rutHash`, correo, fecha de nacimiento ni género; menores abreviados |
| XSS almacenado en el nombre de un torneo | se muestra como texto (React escapa) |
| Inyección de fórmulas y de líneas en el TRF | `'` delante de `= + - @`; saltos de línea a espacio |
| WebSocket sin token o hacia una sala ajena | cierre 1008 / `NOT_IN_ROOM` |
| Cabeceras de seguridad | `nosniff`, `X-Frame-Options: DENY` |

**Hallazgos de esta suite (corregidos):**

- **El TRF no neutralizaba nombres.** Un nombre que empezaba con `=` se ejecutaba al abrirlo en una planilla, y un
  salto de línea podía inyectar un jugador falso. Se corrigió con `TrfExporter.cell`, con prueba unitaria y E2E.
- **Un método HTTP no soportado respondía 500** y llenaba el log de errores, por ejemplo un GET donde solo existe PUT.
  Ahora responde 405, y también se agregaron 415 y 400 por parámetro faltante. Está en
  `libs/common/GlobalExceptionHandler`, con prueba.

**Se verifica solo en la nube:**

- el throttling de API Gateway;
- la CSP y HSTS de la entrada.

Allí se revisa con pedidos manuales y en modo pasivo, nunca con un escaneo activo contra el lab.

**Pendiente (requiere una enmienda del ADR-0002 antes de entrar al CI):**

- **OWASP ZAP:** baseline pasivo en el CI y escaneo activo local con la sesión del IdP simulado.
- **Mutación con PIT** sobre `pairing/`, `standings/`, `rating/` y `rules/`: medir si las pruebas detectan errores
  reales. Sería informativo, no un gate.

## 4. Integración e interacción con otros sistemas

| Interacción | Cómo se prueba |
|---|---|
| Servicios ↔ PostgreSQL | Testcontainers (Postgres 16) en las pruebas de integración de cada servicio |
| Eventos game/tournament → SNS → SQS → users | LocalStack 4.14 (`UsersEventsIntegrationTest`) y E2E (el ELO se actualiza tras la partida) |
| users → SNS → Lambda → SNS → users | E2E: ficha federativa (`federation.lookup.requested`) y Lichess/Chess.com (`external.ratings.sync.requested`) por el receptor SNS local, que llama al mismo handler que la nube |
| Federación, FIDE, Lichess y Chess.com | dobles locales y transporte simulado en pytest: timeout, 429, 5xx, cuenta inexistente, cambio de esquema; ritmo máximo, reintentos con espera y circuit breaker |
| Entra External ID | IdP simulado en local y en el CI; en la nube, prueba manual con dos cuentas Google reales (login, token vencido, cierre de sesión) cuando esté el tenant |
| Contrato de eventos | `docs/events.md` es la fuente; productores y consumidores tienen pruebas con el JSON del contrato |

## 5. Tiempos de consulta y rendimiento

| Medición | Objetivo | Cómo | Último resultado |
|---|---|---|---|
| Lecturas públicas (ranking, búsqueda), p95 | < 300 ms | `scripts/bench` (1 CPU, 1 GB, 10.000 jugadores) | 166–181 ms (28-09) |
| Evento `elo.updated` → visible en la API | < 1 s | `scripts/bench` (SNS → SQS en LocalStack) | p50 26 ms, máx. 58 ms |
| Jugada → la ve el rival | < 1 s | E2E (la jugada aparece sola en el otro navegador) | sin esperas fijas en la prueba |
| Pantalla del monitor tras un resultado | < 3 s | E2E `sala-apoderado.spec.ts` (long polling por versión) | la tabla cambia sin recargar |
| Ratings de Lichess/Chess.com tras vincular | segundos | E2E `jugador.spec.ts` | dentro de la espera de la prueba |

## 6. Resultados de la última verificación

Ver la tabla al final de `flujos-e2e.md`.
