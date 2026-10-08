# Verificación de punta a punta — jugador, organizador y sala de juego

Recorridos reales en el navegador (Chromium, Playwright) contra el stack local completo. No se usa ningún servicio
externo real:

- el IdP simulado hace de Entra External ID;
- una Federación falsa responde con el mismo contrato GraphQL y datos ficticios;
- Lichess y Chess.com falsos (`e2e/support/platforms_stub.py`, puerto 8097) responden a los usernames que empiezan
  con `e2e`.

```bash
make e2e     # levanta infra + users/tournament/game + receptor SNS del ETL + fuentes falsas + web, prueba y apaga
bash scripts/e2e.sh e2e/seguridad.spec.ts   # un solo archivo (los argumentos pasan a Playwright)
```

Pruebas: `apps/web/e2e/` (`jugador.spec.ts`, `seguridad.spec.ts`, `desafio-abierto.spec.ts`, `organizador.spec.ts`, `torneo-completo.spec.ts`, `sala-apoderado.spec.ts`, `roster-invitacion.spec.ts`, `sala.spec.ts`, `respaldo.spec.ts`, `vistas.spec.ts`). Capturas en escritorio y en
375 px en `apps/web/e2e/capturas/` (no se versionan).

## Qué se verifica

### Jugador (`jugador.spec.ts`)
1. Login desde la portada ("Entrar con mi correo") con los claims que entrega Entra (email, nombre, apellido).
2. **Asistente de bienvenida** (3 pasos, axe en el resultado): ritmo favorito **rápido**, cuentas (se dejan vacías)
   y región. Termina con los siguientes pasos y no vuelve a aparecer.
3. **Vincular mi ficha federativa** → `users` publica `federation.lookup.requested` → el ETL (la Lambda; en local, su receptor SNS) consulta la
   Federación (falsa) → `rating.updated` → la tarjeta muestra el **ELO nacional** traído de la ficha.
4. En **Mi perfil** vincula Lichess y Chess.com → `users` publica `external.ratings.sync.requested` → la Lambda
   `external-ratings` (en local, el receptor SNS) consulta las plataformas (falsas) → dos `rating.updated` → el inicio
   muestra los ratings de **Lichess** y de **Chess.com**.
5. Buscar a otro jugador por nombre y **Desafiar**: el ritmo propuesto es el favorito (rápida 10+0), con blancas.
6. El rival ve el desafío en **Mis partidas** y lo acepta; quien desafió se entera solo.
7. **Partida en vivo** entre dos navegadores, jugada a jugada, hasta el mate (1.e4 e5 2.Ac4 Cc6 3.Dh5 Cf6 4.Dxf7#).
8. Ambos ven el resultado ("Ganaste/Perdiste · jaque mate"), el cambio de rating y el PGN descargable.
9. El **ELO ChessQuery** del ritmo se actualiza en el inicio (`elo.updated` con el ritmo → cola → `users`).

### Seguridad: pruebas de abuso (`seguridad.spec.ts`)
Caja negra, **solo contra el stack local**. Cada intento debe fallar de forma segura:
1. **JWT:** sin token, `alg:none`, sujeto cambiado con la firma vieja, firma alterada, audiencia ajena y emisor ajeno → 401.
2. **IDOR y escalamiento:** un jugador no edita, genera rondas, cierra ni lee inscritos o el TRF de un torneo ajeno; no
   asigna tableros en una sala ajena ni invita a un jugador de otro roster; nadie juega una partida que no es suya → 403/404.
3. **Tokens:** la invitación tiene 22 caracteres base64url (128 bits); tokens adivinados de invitación, desafío abierto y
   acreditación dan el mismo 404, sin enumeración.
4. **Ley 21.719:** la vista pública del torneo, la sala en vivo y el ranking no traen `rut`, `rutHash`, `email`,
   `birthDate` ni `gender`; un menor sale con el apellido abreviado en su perfil público.
5. **XSS almacenado:** un torneo llamado `<img src=x onerror=…>` se ve como texto y no ejecuta nada.
6. **Fórmulas en el TRF:** un jugador llamado `=HYPERLINK(…)` sale con `'` delante; un salto de línea en el nombre del
   torneo no inyecta líneas.
7. **WebSocket:** sin token se cierra (1008); con token, un extraño no se suscribe a una sala ajena (`NOT_IN_ROOM`).
8. **Cabeceras y errores:** `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`; un recurso inexistente da 404 y un
   método no soportado 405, ambos con `{status, error, message, timestamp}`.

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

### Torneo con reglas reales (`torneo-completo.spec.ts`)
1. Roster por CSV y torneo con **cupo 8, aprobación del organizador y acreditación con QR**.
2. El roster se inscribe **en bloque**; una jugadora con cuenta se inscribe sola y queda **pendiente** hasta que el
   organizador la **aprueba**; ella ve su **QR de acreditación**.
3. **Credenciales para imprimir** con el código de cada jugador.
4. El día del torneo: **acreditación** con el código de la credencial y a mano desde la lista; uno no llega.
5. La **ronda 1** empareja solo a los 4 acreditados; el ausente queda como **"no se presentó"**.
6. Una jugadora se **retira**: la ronda 2 empareja a los 3 que siguen (mesa + bye) y la vista pública muestra el retiro.

### La sala del torneo en vivo (`sala-apoderado.spec.ts`)
1. El organizador genera la ronda 1; la **pantalla del monitor** (`/torneos/:id/pantalla`, sin login, 1920×1080)
   muestra los emparejamientos en grande y un **QR** para seguir el torneo desde el celular.
2. Un **apoderado sin cuenta**, en su celular (375 px), busca a su hijo y lo **sigue**: ve su mesa, color y rival.
3. El organizador carga los resultados: la pantalla (con la rotación en pausa) y el celular se **actualizan solos**,
   sin recargar (long polling por versión del torneo); el apoderado ve el resultado y su posición en la tabla.

### Roster e invitación a reclamar el perfil (`roster-invitacion.spec.ts`)
1. El organizador importa a una alumna por **CSV en el servidor** y la **inscribe en un torneo** en el mismo paso.
2. Genera su **invitación** (enlace + QR, un solo uso, 30 días).
3. La alumna abre el enlace **sin sesión**, entra con su cuenta (otro email) y **reclama** su perfil.
4. El torneo pasa a su cuenta (`player.merged` → tournament reescribe la inscripción).

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

## Semilla para la demo (`make demo-seed`)

Con `make dev` corriendo, `make demo-seed` (`scripts/demo_seed.py`) deja listo:

- el club «Club Demo Andino» con 16 jugadores ficticios;
- la *Liga Demo* en curso, con 2 rondas jugadas, para la pantalla del monitor;
- el *Abierto Demo* con inscritos y acreditación por QR;
- la sala «Clase 4°B» con 4 tableros;
- tres cuentas: la organizadora, Valentina (ve la bienvenida) y Tomás (con Lichess y Chess.com vinculados, amigo de
  Valentina).

Imprime cómo entrar (sujeto y claims para el IdP simulado) y las URL de cada historia. Se puede repetir: busca lo
que ya existe antes de crearlo (verificado con dos corridas seguidas).

## Resultados (08-10-2026)

Con la máquina descargada:

| Prueba | Resultado |
|---|---|
| Java: libs y servicios users, tournament y game (unitarias + integración con Testcontainers y LocalStack) | ✅ 147, cobertura ≥ 90 % por módulo |
| ETL (pytest) | ✅ 60, cobertura 99 % |
| Web (vitest + axe) | ✅ 71 en 15 archivos |
| Suite E2E completa: 9 recorridos + 8 pruebas de seguridad | ✅ 17 de 17, dos corridas seguidas (1,7 y 1,9 min) |
| `make complexity` · `make tf-check` · `tsc --noEmit` | ✅ |
| `make demo-seed` dos veces seguidas | ✅ la segunda no duplica nada |

## Resultados anteriores (29-09-2026)

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
