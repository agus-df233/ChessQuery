import { ReactNode, useCallback, useState } from 'react';
import { ConfirmDialog } from '@chessquery/ui-lib';

export interface ConfirmOptions {
  title: string;
  message: ReactNode;
  confirmLabel: string;
  /** `danger` para lo que no se puede deshacer (abandonar, cerrar la sala). */
  tone?: 'danger' | 'default';
}

/**
 * Confirmación accesible en lugar de `window.confirm` (que no se puede estilar ni probar y bloquea la página):
 * `if (await ask({...})) accion()`, renderizando `dialog` en el mismo componente.
 */
export const useConfirm = () => {
  const [pending, setPending] = useState<{ options: ConfirmOptions; resolve: (ok: boolean) => void } | null>(null);
  const ask = useCallback((options: ConfirmOptions) => new Promise<boolean>((resolve) => setPending({ options, resolve })), []);
  const finish = (ok: boolean) => { pending?.resolve(ok); setPending(null); };
  const dialog = (
    <ConfirmDialog open={pending !== null} title={pending?.options.title ?? ''} message={pending?.options.message ?? ''}
                   confirmLabel={pending?.options.confirmLabel} tone={pending?.options.tone}
                   onConfirm={() => finish(true)} onClose={() => finish(false)} />
  );
  return { ask, dialog };
};
