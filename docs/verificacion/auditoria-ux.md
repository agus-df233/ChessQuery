# Auditoría de experiencia: vista por vista (10-10-2026)

**Cómo se hizo.** `apps/web/e2e/galeria.spec.ts` recorre **31 vistas y estados** (público, jugador, organizador y la
bienvenida) en **375, 768 y 1280 px**. Arma por API un escenario con datos reales:

- un club con roster;
- un torneo abierto con aprobación y QR;
- una liga en la ronda 2;
- una sala con tableros asignados;
- dos amigos con una partida en curso, un desafío abierto y una invitación.

En cada vista guarda la captura en `e2e/capturas/galeria/` (no se versiona), corre axe y mide:

- scroll horizontal;
- controles cortados;
- objetivos táctiles de menos de 24 px;
- títulos pegados al borde.

La salida queda en `hallazgos.json`. Las capturas se revisaron a mano, comparándolas con la v2
(`agusnoopy3000/ChessQuery_FS3`, apps `chess-portal` y `organizer-panel`).

**Resultado de la medición automática:**

- 0 violaciones de axe;
- 0 scroll horizontal del documento;
- 0 controles cortados.

Las tablas se desplazan dentro de su propio contenedor. Lo que sigue es lo que la medición no capta o capta como aviso.

Severidad: **A** = rompe la experiencia o la confianza · **B** = se nota y molesta · **C** = pulido.

## Hallazgos globales (afectan a todas las vistas)

| # | Hallazgo | Sev. | Arreglo |
|---|---|---|---|
| G1 | `<main>` del `Shell` sin padding: en escritorio el contenido queda pegado a la barra lateral y el título se corta arriba; en 768 px los títulos y las tarjetas tocan los bordes (25 vistas marcadas). En móvil lo parchaba `.cq-page` con 12 px | A | Padding en `.main` de la ui-lib (`clamp(16px, 3vw, 32px)`) y se quita el parche de `.cq-page` |
| G2 | La portada no muestra los dos caminos (jugador y organizador); solo hay un botón de Google | A | Hero y dos tarjetas de acceso, como la v2 (`Home.tsx`) |
| G3 | El menú del organizador mezcla todas las secciones del jugador; el rol solo aparece como subtítulo | B | Selector «Jugador \| Organizador» en la cabecera, con un menú por modo |
| G4 | Textos en inglés: pie del menú «Player»/«Organizer»; selector de archivo «Choose File / No file chosen» | B | Español en el pie; control de archivo propio («Elegir archivo CSV») |
| G5 | Íconos del menú mezclados (emojis de color 🔍🏆👥👤 junto a piezas ♔♞) | C | Un solo estilo monocromo (glifos de ajedrez y símbolos simples) |
| G6 | Confirmaciones con `window.confirm` del navegador (abandonar partida, retirar jugador, cerrar sala) | B | `ConfirmDialog` de la ui-lib (accesible, con foco atrapado) |
| G7 | Sin mensajes emergentes: los éxitos y errores de acciones rápidas aparecen lejos de la acción o no aparecen | B | `Toast` en la ui-lib (`role=status`/`alert`) |
| G8 | Sesión vencida: un 401 muestra «El servidor respondió con un error»; no hay aviso de cierre de sesión | A | Renovación silenciosa ante 401; si falla, aviso y login conservando la página; aviso «Cerraste sesión» |
| G9 | `select` e `input` nativos de menos de 24 px de alto en móvil (resultado de mesa, color y ritmo del desafío, coronación, asignación de tableros, buscadores) | B | Estilo común de formularios: alto mínimo de 40 px, fuente de 16 px en móvil (evita el zoom de iOS) |
| G10 | Falta el enlace «Saltar al contenido» | C | Enlace oculto hasta recibir el foco, apuntando a `<main id="contenido">` |

## Hallazgos por vista

| Vista | Hallazgo | Sev. | Arreglo |
|---|---|---|---|
| Portada | Ver G2. En 375 px ocupa poco y no dice para quién es | A | Hero, tarjetas por rol y enlaces públicos (ranking, torneos, seguir un torneo) |
| Torneo (organizador y público), rondas | En 375 px la columna de **negras** se corta y obliga a desplazar dentro de la tabla | A | Bajo 600 px, una **tarjeta por mesa**: «Mesa 1 · Blancas – Negras» y el resultado a todo el ancho |
| Clasificación | En 375 px se cortan las columnas de desempate (SB, V) | B | En móvil: #, jugador y puntos; los desempates se despliegan por fila |
| Inscritos, roster, ranking | Tablas anchas en 375 px | B | Lista compacta en móvil (nombre, ELO y acción) |
| Partida | Título pegado a la barra superior; «Coronar a» es un `select` nativo diminuto | B | Margen superior (G1); coronación con 4 botones de pieza al mover un peón a la última fila |
| Sala (organizador) | Asignación con `select` nativos chicos; etiquetas en línea; 4 tableros grandes en columna en el celular | B | Selectores con el estilo común y etiqueta arriba; en el celular, tableros en 2×2 compactos con «ver en grande» |
| Desafío (formulario) | Ritmo y color con `select` chicos | C | Botones de ritmo (presets) como chips seleccionables |
| Club | Carga masiva con el control de archivo nativo en inglés | B | Ver G4 |
| Inicio del jugador | Sin datos, cuatro tarjetas vacías seguidas («Todavía no hay ratings», «Sin historial») | C | Estado vacío con la próxima acción: «Juega tu primera partida», «Vincula tu ficha» |
| Menú móvil | Funciona (foco, Escape, cierre al navegar); el pie dice «Player» | C | G4 |
| Pantalla de la sala (TV) | Correcta en 1920 px; en 768 px los paneles se apilan | C | Sin cambios: es para monitor |

## Orden de trabajo

1. **G1–G4 y G8:** márgenes, portada, roles, textos y sesión. Es lo que más se nota en la demo.
2. **G6, G7 y G9:** diálogos, avisos y formularios.
3. **Responsive por vista:** rondas, clasificación, tablas, partida y sala.
4. **G5, G10** y estados vacíos.

La galería se vuelve a correr después de cada bloque. Al final, sus avisos de títulos pegados y de objetivos chicos
pasan a ser obligatorios (`expect`, ya no `expect.soft`).
