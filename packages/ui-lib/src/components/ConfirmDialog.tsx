import { ReactNode } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';

export interface ConfirmDialogProps {
  open: boolean;
  title: ReactNode;
  message: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** `danger` para acciones destructivas (eliminar); `default` para el resto. */
  tone?: 'danger' | 'default';
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

/**
 * Diálogo de confirmación accesible (reemplaza a `window.confirm()`).
 * Hereda role="dialog", focus-trap y Escape del `Modal` base.
 */
export const ConfirmDialog = ({
  open,
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  tone = 'default',
  loading = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps) => (
  <Modal
    open={open}
    onClose={onClose}
    title={title}
    size="sm"
    closeOnOverlay={!loading}
    footer={
      <>
        <Button variant="ghost" onClick={onClose} disabled={loading}>
          {cancelLabel}
        </Button>
        <Button
          variant={tone === 'danger' ? 'danger' : 'primary'}
          onClick={onConfirm}
          loading={loading}
        >
          {confirmLabel}
        </Button>
      </>
    }
  >
    <div style={{ color: 'var(--text-muted)', lineHeight: 1.6, fontSize: 14 }}>{message}</div>
  </Modal>
);
