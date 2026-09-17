# ADR-0001 — Arquitectura v3: servicios Java como resource servers, IdaaS y realtime propio

- **Estado:** Aceptada (2026-09-16)
- **Decide:** Agustín Garrido, Martín Mora

## Contexto

La v2 (repo `ChessQuery_FS3-v2`) funcionaba de punta a punta, pero cargaba con piezas que no
eran lógica de negocio: un API Gateway propio que validaba JWT de Supabase y mapeaba usuarios,
dos BFFs NestJS (3.487 líneas, ~1.050 de proxy puro), un webhook de registro, Realtime y
Storage de Supabase con canales públicos sin autenticación, y código duplicado en cinco servicios
(handler de errores, idempotencia, config de RabbitMQ). Una auditoría del 16-09-2026 mostró que los
servicios Java eran ya agnósticos del proveedor de identidad.

## Decisión

1. **Identidad delegada a Entra External ID** (tenant CIAM) con Google como proveedor social:
   un único issuer. El frontend hace Authorization Code + PKCE (`react-oidc-context`). No hay
   pantallas propias de registro, login ni recuperación de contraseña.
2. **Sin gateway propio.** Cada servicio es un OAuth2 Resource Server (`issuer-uri` + audiencia).
   El ALB enruta por path; el rate limiting lo hace WAF. Defensa en profundidad: ningún servicio
   confía en cabeceras `X-User-*`.
3. **Identidad interna** resuelta por `libs/auth-starter`: `sub` → `player_id` consultando `users`
   (`/internal/players/by-subject`, caché 5 min, provisión JIT al primer request). Los roles de
   negocio viven en nuestra BD: `ORGANIZER` es ser dueño de una `Organization`; `ADMIN` es un app
   role del IdP.
4. **Cuatro servicios Java** (`users`, `tournament`, `game`, `notifications`) + `etl` Python
   programado. `game` absorbe la analítica (rollups por evento `game.finished`). Sin BFF: cada
   dueño enriquece sus respuestas usando un endpoint batch de `users`.
5. **Realtime propio** en `game`: Spring WebSocket/STOMP con relay a RabbitMQ para múltiples
   réplicas; handshake autenticado con el mismo JWT; ofertas de tablas y fin de partida por REST
   validado. Plan B gestionado: AWS AppSync Events.
6. **Una instancia RDS** con un schema por servicio; S3 con URLs prefirmadas para PGN y
   comprobantes; Amazon MQ (RabbitMQ) con el exchange `ChessEvents`; SES para email.
7. **Infra como código** (Terraform) y CD por GitHub Actions con OIDC hacia AWS.

## Consecuencias

- Se elimina: api-gateway, ambos BFFs, webhook de Supabase, Supabase Auth/Realtime/Storage,
  el rol auto-declarado al registrarse.
- Hay que reescribir en Java ~650 líneas de lógica que vivía en los BFFs (agregaciones, matchmaking,
  transición OPEN→IN_PROGRESS al generar ronda, whitelist de PII) y todo `playerId` pasa a salir
  del token.
- Servicios internos se llaman con `X-Internal-Token` (secreto compartido) además del aislamiento
  de red; si el equipo crece, migrar a client credentials del propio IdP.
- Costo de operar un tenant CIAM y credenciales OAuth de Google; a cambio desaparecen tres
  componentes propios y ~150 tests de plomería.
