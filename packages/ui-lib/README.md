# @chessquery/ui-lib

Design system de ChessQuery: componentes React y el tema (oscuro, contraste AA). Lo consume `apps/web` como paquete
del workspace de npm (`"@chessquery/ui-lib": "*"`). No se publica en un registro. Importar cualquier componente ya
aplica el tema (`src/theme/theme.css`).

```tsx
import { Button, Card, ConfirmDialog, Table, useToast } from '@chessquery/ui-lib';
```

| Componente | Para qué |
|---|---|
| `Shell` | Marco de la app: menú lateral (cajón en el celular), selector de modo, «Saltar al contenido» y cierre de sesión |
| `Button`, `Card`, `Badge`, `Skeleton`, `EmptyState`, `ErrorAlert` | Piezas básicas |
| `FileInput` | Selector de archivo con texto propio en español (en vez del control nativo del navegador) |
| `Modal`, `ConfirmDialog` | Diálogos accesibles (foco atrapado, Escape, devuelven el foco); reemplazan a `window.confirm` |
| `ToastProvider`, `useToast` | Avisos emergentes: `status` para éxitos, `alert` para errores; se cierran solos a los 5 s |
| `Table` | Tablas con desplazamiento accesible; con `stackOnMobile`, bajo 600 px cada fila es una tarjeta |

Las pruebas de estos componentes (render, comportamiento y axe) viven en `apps/web/src/*.spec.tsx`, junto a las
vistas que los usan.
