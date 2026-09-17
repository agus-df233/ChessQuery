import { ReactNode, useEffect, useId, useRef } from 'react';
import { useFocusTrap } from '../utils/useFocusTrap';

type Size = 'sm' | 'md' | 'lg';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  size?: Size;
  closeOnOverlay?: boolean;
  /** Etiqueta accesible cuando no se pasa `title` (ej. modales sin cabecera). */
  ariaLabel?: string;
}

const SIZE_MAX: Record<Size, number> = { sm: 380, md: 520, lg: 800 };

export const Modal = ({
  open,
  onClose,
  title,
  children,
  footer,
  size = 'md',
  closeOnOverlay = true,
  ariaLabel,
}: ModalProps) => {
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', handler);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  useFocusTrap(dialogRef, open);

  if (!open) return null;

  return (
    <div className="overlay" onClick={closeOnOverlay ? onClose : undefined}>
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-label={!title ? ariaLabel : undefined}
        tabIndex={-1}
        className="modal fade-up"
        style={{ maxWidth: SIZE_MAX[size] }}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '16px 20px',
              borderBottom: '1px solid var(--border)',
            }}
          >
            <div
              id={titleId}
              style={{ fontFamily: "'Space Grotesk', system-ui, sans-serif", fontWeight: 600, fontSize: 15 }}
            >
              {title}
            </div>
            <button
              type="button"
              onClick={onClose}
              className="btn btn-ghost"
              style={{ padding: '4px 8px' }}
              aria-label="Cerrar"
            >
              ✕
            </button>
          </div>
        )}
        <div style={{ padding: 20 }}>{children}</div>
        {footer && (
          <div
            style={{
              padding: '12px 20px',
              borderTop: '1px solid var(--border)',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: 8,
            }}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  );
};
