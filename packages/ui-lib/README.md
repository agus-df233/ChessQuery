# @chessquery/ui-lib

Componente **NPM** del proyecto ChessQuery: librería de componentes React
compartida, consumida por las apps del monorepo (`chess-portal`,
`organizer-panel`) vía **NPM workspaces**.

- **Nombre del paquete:** `@chessquery/ui-lib`
- **Versión:** `0.1.0`
- **Entrypoint:** `src/index.ts` (`main` y `types`)
- **Peer dependencies:** `react ^18.2.0`, `react-dom ^18.2.0`

> Este es el componente frontend de tipo NPM evaluado en la rúbrica
> (Parcial N°2). No se publica a un registry público: se distribuye como
> workspace package y se resuelve por symlink dentro de `frontend/`.

## Instalación / consumo

El paquete ya está declarado como dependencia de workspace en cada app:

```jsonc
// apps/<app>/package.json
"dependencies": {
  "@chessquery/ui-lib": "*"
}
```

Desde la raíz `frontend/`, `npm install` enlaza el paquete a las apps
automáticamente (workspaces). Uso en código:

```tsx
import { Button, PlayerCard, StandingsTable, cn } from '@chessquery/ui-lib';

<Button variant="primary" onClick={save}>Guardar</Button>
```

El entrypoint importa `./theme/theme.css`, por lo que el tema se aplica
con sólo importar cualquier componente.

## Componentes y utilidades exportados

| Export | Tipo | Descripción |
|---|---|---|
| `Button` | Componente (`forwardRef`) | Botón con variantes |
| `Input` | Componente (`forwardRef`) | Campo de texto controlado |
| `Select` | Componente (`forwardRef`) | Selector con `SelectOption[]` |
| `Card` | Componente | Contenedor con header/footer opcionales |
| `Badge` | Componente | Etiqueta de estado (dot/pulse) |
| `Skeleton` | Componente | Placeholder de carga |
| `Modal` | Componente | Diálogo con overlay |
| `Table<T>` | Componente genérico | Tabla tipada por columnas |
| `RatingBadge` | Componente | Insignia de ELO |
| `PlayerCard` | Componente | Tarjeta de jugador |
| `ChessBoard` | Componente | Tablero a partir de FEN |
| `StandingsTable` | Componente | Tabla de posiciones con desempates |
| `EmptyState` | Componente | Estado vacío |
| `ErrorAlert` | Componente | Alerta de error con retry |
| `Shell` | Componente | Layout de app (nav + usuario) |
| `cn` | Utilidad | Merge de clases (adapter sobre `clsx`) |

## Patrones de diseño aplicados (relevante para la defensa)

- **Component Composition** — `Card`, `Shell`, `Modal` aceptan `children`
  y slots (`header`/`footer`); las apps componen UI con primitivas.
- **Generics** — `Table<T>` parametriza el tipo de fila vía
  `TableColumn<T>`, reutilizable sin perder tipado.
- **Forward Ref** — `Button`, `Input`, `Select` reenvían `ref` al nodo
  nativo para integrarse con formularios/focus.
- **Adapter** — `cn()` adapta `clsx` a una API estable e interna, aislando
  la dependencia externa del resto de la librería.

## Build y verificación de tipos

```bash
cd frontend/packages/ui-lib
npm run build        # tsc -p tsconfig.json --noEmit (type-check)
```

La librería se distribuye como TypeScript fuente (no hay bundle): cada app
la transpila con su propio pipeline de Vite. `npm run build` valida que el
paquete compile sin errores de tipos.

## Pruebas

Las pruebas de los componentes corren con la suite Vitest del monorepo
(configurada en la raíz `frontend/`):

```bash
cd frontend
npx vitest run                # toda la suite (incluye componentes de ui-lib)
npx vitest run --coverage     # con cobertura (v8)
```

## Estructura

```
packages/ui-lib/
├── package.json
├── tsconfig.json
└── src/
    ├── index.ts            # entrypoint: re-exporta componentes + cn
    ├── components/         # 15 componentes React
    ├── theme/theme.css     # variables de tema (importado por index.ts)
    └── utils/cn.ts         # helper de className (adapter de clsx)
```
