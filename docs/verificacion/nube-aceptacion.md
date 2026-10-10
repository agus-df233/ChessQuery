# Aceptación en la nube: del Jugador al Organizador

Checklist para probar ChessQuery **desplegado** (Learner Lab) con login real con Google (Amazon Cognito). Se recorre
completo antes de dar la nube por funcional. La misma lista, en versión corta, se usa antes en local con
`make dev-idp` (fase C del plan).

**Antes de empezar:**

- `make cloud-smoke` sin fallas.
- La URL de la app es la salida `app_url`; Terraform ya la registró como callback del cliente de Cognito.

## Personas y equipos

| Rol | Cuenta | Dispositivo |
|---|---|---|
| **A · Jugadora** | Google 1 | computador (ventana normal) |
| **B · Jugador** | Google 2 | computador (incógnito) o celular |
| **O · Organizador** | Google 3 (o la misma de B en otra sesión) | computador |
| **M · Monitor** | sin cuenta | otra pestaña o TV |
| **P · Apoderado** | sin cuenta | celular |

Material: `docs/demo/roster-demo.csv` (16 jugadores ficticios).

Para cada paso se anota ✅, ❌ o ⚠️ con lo observado. Si algo falla: hora aproximada y captura. Con eso se buscan los
logs en CloudWatch.

## 1. Jugador: entrar, integrarse y jugar

| # | Quién | Paso | Resultado esperado | Resultado |
|---|---|---|---|---|
| 1.1 | A | Abrir `app_url` → **Continuar con Google** | Va directo a Google; al volver: «Hola, <nombre>» con el nombre de la cuenta | |
| 1.2 | A | Asistente de bienvenida: ritmo **Relámpago**, cuentas vacías, región → **Terminar** → **Ir a mi inicio** | «¡Listo! Tu perfil quedó configurado»; al recargar ya no aparece | |
| 1.3 | A | Inicio → tarjeta Federación → **Mi id federativo** (uno real tuyo, o de prueba) → **Vincular mi ficha** | «Ficha federativa N»; en segundos (Lambda `federation-lookup`) aparece el ELO nacional si la ficha existe | |
| 1.4 | A | **Mi perfil** → Usuario de Lichess y/o de Chess.com (cuentas reales) → Guardar | En segundos (Lambda `external-ratings`) el inicio muestra sus ratings por ritmo | |
| 1.5 | B | Entrar (incógnito) y omitir la bienvenida | «Hola, <nombre>» | |
| 1.6 | A | **Jugadores** → buscar a B → **Agregar amigo**; B → **Amigos** → **Aceptar** | Quedan como amigos en ambos lados | |
| 1.7 | A | Perfil de B → **Desafiar** (ritmo propuesto: relámpago 3+2) → **Enviar desafío** | «Esperando que tu rival acepte…» | |
| 1.8 | B | **Mis partidas** → aceptar | Ambos entran a la partida; A se entera **sin recargar** | |
| 1.9 | A/B | Jugar varias jugadas | Cada jugada aparece en el otro navegador en < 1 s (WebSocket de API Gateway); los relojes corren | |
| 1.10 | A/B | Terminar (mate, abandono o tablas) | Resultado en ambos; PGN descargable; el **ELO ChessQuery relámpago** cambia en el inicio | |
| 1.11 | A | **Mis partidas** → **Crear enlace** (desafío abierto) | Aparece el enlace y el **QR del desafío abierto** | |
| 1.12 | B (celular) | Escanear el QR → entrar si pide → **Aceptar y jugar** | Vuelve al desafío tras el login y la partida empieza en ambos | |
| 1.13 | A | **Cerrar sesión** y volver a entrar con **Continuar con Google** | Pasa por el `/logout` de Cognito y vuelve a la misma cuenta (mismo correo, mismo ELO) | |

## 2. Organizador: de la planilla al TRF

| # | Quién | Paso | Resultado esperado | Resultado |
|---|---|---|---|---|
| 2.1 | O | Entrar → **Crear mi club** → **Nombre del club**, **Ciudad** → **Crear club** | Pasa a ser organizador; aparece la gestión del club | |
| 2.2 | O | **Archivo CSV del roster** → `roster-demo.csv` → **Importar** | «16 para importar» → «Importados 16»; filas con ELO | |
| 2.3 | O | **Torneos del club** → nombre, **Rondas** 4, ritmo 10+5, **Cupo de jugadores** 12, **Apruebo cada inscripción**, **Acreditación con QR el día del torneo** → **Crear torneo** | Torneo abierto con su vista de gestión | |
| 2.4 | O | **Inscribir a todo el roster** | Se llena el cupo (12) y el resto queda en **lista de espera** | |
| 2.5 | A | **Torneos** → el torneo → **Inscribirme** | «Tu inscripción espera la aprobación del organizador» | |
| 2.6 | O | **Esperan tu aprobación** → **Aprobar** a A (retirar a alguien si el cupo está lleno) | A queda inscrita; quien sale libera cupo para la lista de espera | |
| 2.7 | A | Ver **Mi QR de acreditación** | Se muestra el QR | |
| 2.8 | O (celular) | Abrir la acreditación → escanear el QR de A (o **Código de acreditación** → **Acreditar**) | A queda presente; repetirla no duplica | |
| 2.9 | O | Acreditar al resto menos 1 → **Credenciales para imprimir** | Credenciales con QR por jugador | |
| 2.10 | O | **Cerrar inscripciones y generar ronda 1** | Emparejamientos solo con los presentes; el ausente queda fuera | |
| 2.11 | O | Cargar resultados (**Resultado mesa N**) → **Generar ronda 2** → **Retirar** a un jugador → completar las rondas | Tabla con desempates; el retirado no se empareja más | |
| 2.12 | O | **Cerrar torneo** | «Torneo cerrado: ratings enviados»; el ELO del ritmo de A cambia en su inicio | |
| 2.13 | O | **Exportar TRF** | Se descarga el TRF-16 con los nombres completos | |
| 2.14 | O | Roster → invitar a un jugador provisorio | Enlace y QR para reclamar el perfil | |
| 2.15 | B | Abrir ese enlace → **Sí, soy yo: unirlo a mi cuenta** | El perfil (con su historial) pasa a la cuenta de B | |

## 3. La sala en vivo (monitor y apoderados)

| # | Quién | Paso | Resultado esperado | Resultado |
|---|---|---|---|---|
| 3.1 | O | Abrir **Pantalla para la sala (monitor)** de un torneo en curso | Tabla y mesas, con QR; rota cada 15 s; **Pausar rotación** funciona | |
| 3.2 | P (celular) | Escanear el **QR para seguir el torneo desde el celular** (sin cuenta) → **Busca a tu hijo…** → elegir a A | Ve la mesa y el historial de A | |
| 3.3 | O | Cargar un resultado | El monitor y el celular cambian **sin recargar** en < 3 s | |
| 3.4 | P | Revisar los datos de un menor | Apellido abreviado; nunca RUT, correo ni fecha de nacimiento | |

## 4. Clase en el colegio (sala de juego)

| # | Quién | Paso | Resultado esperado | Resultado |
|---|---|---|---|---|
| 4.1 | O | **Salas de juego** → **Nombre de la sala**, 2 tableros, cupo 4 → **Abrir sala** | Código de 6 caracteres y QR | |
| 4.2 | A, B (+1) | **Código de la sala** → **Entrar** | Todos quedan como espectadores: «Estás mirando como espectador…» | |
| 4.3 | O | Asignar el tablero 1 (A blancas, B negras) → **Guardar puestos** → **Iniciar todos los tableros listos** | A y B pasan solos a su partida | |
| 4.4 | A/B | Jugar | La cuadrícula del organizador (4 por pantalla) y la del espectador se mueven en vivo | |
| 4.5 | O | **Cambiar tableros o cupo** → 3 tableros, asignar al espectador | El espectador se entera en su antesala | |
| 4.6 | A | Revisar el ELO | La partida de sala **no** cambió el ELO | |

## 5. Lo que revisa el agente en paralelo (solo lectura)

- `aws logs tail` de users, tournament, game y las Lambdas: sin errores 5xx ni excepciones no controladas.
- DLQ de SQS vacías.
- Targets del ALB sanos y servicios ECS estables, sin reinicios.
- Tiempos de `make cloud-smoke` antes y después de la prueba.

## Resultado

| Sección | Estado | Observaciones |
|---|---|---|
| 1 · Jugador | | |
| 2 · Organizador | | |
| 3 · Sala en vivo | | |
| 4 · Clase | | |
