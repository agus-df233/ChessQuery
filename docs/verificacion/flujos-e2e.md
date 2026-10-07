# Verificación de punta a punta — jugador, organizador y sala de juego

Recorridos reales en el navegador (Chromium, Playwright) contra el stack local completo. No se usa ningún servicio
externo real: el IdP simulado hace de Entra External ID y una Federación falsa responde con el mismo contrato
GraphQL y datos ficticios.

```bash
make e2e     # levanta infra + users/tournament/game + receptor SNS del ETL + Federación falsa + web, prueba y apaga
```

Pruebas: `apps/web/e2e/` (`jugador.spec.ts`, `desafio-abierto.spec.ts`, `organizador.spec.ts`, `sala.spec.ts`, `respaldo.spec.ts`, `vistas.spec.ts`). Capturas en escritorio y en
375 px en `apps/web/e2e/capturas/` (no se versionan).

## Qué se verifica

### Jugador (`jugador.spec.ts`)
1. Login desde la portada ("Entrar con mi correo") con los claims que entrega Entra (email, nombre, apellido).
2. **Vincular mi ficha federativa** → `users` publica `federation.lookup.requested` → el ETL (la Lambda; en local, su receptor SNS) consulta la
   Federación (falsa) → `rating.updated` → la tarjeta muestra el **ELO nacional** traído de la ficha.
3. Buscar a otro jugador por nombre, **Desafiar** (relámpago 3+2, con blancas; el ritmo define qué ELO ChessQuery se juega).
4. El rival ve el desafío en **Mis partidas** y lo acepta; quien desafió se entera solo (long polling).
5. **Partida en vivo** entre dos navegadores, jugada a jugada, hasta el mate (1.e4 e5 2.Ac4 Cc6 3.Dh5 Cf6 4.Dxf7#).
6. Ambos ven el resultado ("Ganaste/Perdiste · jaque mate"), el cambio de rating y el PGN descargable.
7. El **ELO ChessQuery relámpago** del inicio se actualiza (`elo.updated` con `PLATFORM_BLITZ` → cola → `users`).

### Desafío abierto: integrarse jugando (`desafio-abierto.spec.ts`)
1. Ana publica un **desafío abierto** (relámpago 3+2, con blancas): obtiene un enlace con token aleatorio y su **QR**.
2. Bruno, que no es su amigo y **no tiene sesión**, abre el enlace: entra con su cuenta y **vuelve al desafío** (el
   login conserva la página pedida).
3. Bruno ve las condiciones y **acepta**; los dos quedan en la misma partida (Ana se entera sola) y juegan.

### Organizador (`organizador.spec.ts`)
1. Crea su club y carga el roster por **CSV** (5 jugadores, vista previa y confirmación).
2. Crea un **torneo suizo** de 3 rondas, inscribe a todo el roster.
3. Genera las 3 rondas (2 mesas + bye con 5 jugadores) y carga resultados por mesa.
4. **Cierra el torneo** (ratings enviados) y **exporta el TRF** (5 líneas `001`, cabecera con el nombre).
5. La **vista pública** del torneo (sin login) muestra la clasificación; `/torneos` lista el torneo y el
   calendario de la Federación.

### Sala de juego: la clase en el colegio (`sala.spec.ts`)
1. El profesor crea su club y abre una **sala de 2 tableros con cupo 5**; se ven el **código** y el **QR**.
2. Entran **5 alumnos** con el enlace del QR (el código en la URL, en minúsculas); todos quedan como espectadores.
3. El profesor **asigna** los tableros 1 y 2 (el quinto alumno sigue mirando) e **inicia todos**.
4. Cada alumno asignado **pasa solo a su partida**; el de blancas del tablero 1 juega e4.
5. La jugada aparece **sola** en la cuadrícula del profesor y en la del espectador (4 tableros por pantalla, en vivo).
6. El profesor **sube a 3 tableros** y le da puesto al espectador, que se entera en su antesala.
Las partidas de sala no cuentan para el ELO (lo verifica `RoomFlowIntegrationTest`).

### Todas las vistas (`vistas.spec.ts`)
Inicio, Mi perfil, Jugadores, Ranking, Mis partidas, Amigos, Torneos, Crear mi club, portada y ranking público:
cargan sin errores de JavaScript, **sin violaciones de axe (WCAG 2 A/AA)** y **sin scroll horizontal en 375 px**.
Las vistas de partida, club, torneo (organizador y público) se revisan igual dentro de los recorridos.

## Resultados (29-09-2026)

| Prueba | Resultado |
|---|---|
| Unitarias e integración Java (users, tournament, game, libs) | ✅ 108, cobertura ≥ 90 % por módulo |
| ETL (pytest) | ✅ 48, cobertura 98 % |
| Web (vitest + axe) | ✅ 35 (tres corridas seguidas) |
| E2E todas las vistas | ✅ |
| E2E jugador (ficha → desafío → partida → rating) | ✅ en ~10–19 s |
| E2E organizador (club → torneo → TRF → vista pública) | ✅ en ~9 s |
| Suite E2E completa | ✅ en 9 de 10 corridas; 1 falla del recorrido del jugador justo después de reiniciar `users` (causa no identificada, ver límites) |
| `make complexity` (CCN ≤ 10) · `make tf-check` | ✅ |

## Problemas que encontró la verificación (y quedaron corregidos)

- **Un RUT repetido en una ficha tumbaba el lote completo de `rating.updated`** (hasta 200 jugadores de la carga
  mensual): el índice único del hash del RUT fallaba al confirmar la transacción. Ahora un identificador que ya
  pertenece a otro jugador no se copia, se registra el conflicto y el resto del lote se aplica (prueba de regresión
  en `UsersIntegrationTest`).
- **El reloj de la partida era ilegible en tema oscuro** (contraste 1,03:1, usaba una variable de color
  inexistente). Corregido con los colores del tema; lo detectó axe.
- **Concordancia en el tablero accesible**: "dama blanco" → "dama blanca" (lo detectó una prueba de la web).
- En la nube, `POST /api/organizations` (crear club) y `GET /api/friends` no estaban enrutados en el ALB, y la regla
  de `users` excedía el límite de 5 condiciones por regla de AWS: corregido en Terraform.
- En el celular, el selector de resultado empujaba fuera de la tabla la columna de negras de la ronda en curso, y el
  contenido de todas las vistas tocaba los bordes de la pantalla: ancho acotado y margen lateral de 12 px.
- El selector "Coronar a" seguía visible con la partida terminada: ahora solo aparece mientras se juega.
- El límite de 5 consultas por segundo a la Federación funciona: un worker mal configurado se rechaza y el pedido
  queda en la cola para reintento (no se pierde).

## Límites conocidos

- **Intermitencia pendiente:** el recorrido del jugador falló 1 vez (justo después de reiniciar `users`) y pasó en
  las corridas siguientes. No quedó registrada la causa porque Playwright reemplaza los resultados de cada corrida;
  si se repite, revisar `apps/web/e2e/.resultados/` (trace) antes de volver a correr.

- El tenant real de Entra (Google) aún no existe: el login real se prueba con la guía local de configuración.
- El aviso en vivo de partidas vive en memoria de cada instancia de `game` (1 réplica en el MVP); ver la enmienda
  de ADR-0002.
