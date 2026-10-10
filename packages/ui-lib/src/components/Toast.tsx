import { ReactNode, createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { cn } from '../utils/cn';

export type ToastKind = 'success' | 'error' | 'info';

interface ToastItem { id: number; kind: ToastKind; message: string }

type Push = (kind: ToastKind, message: string) => void;

const ToastContext = createContext<Push>(() => undefined);

/** Cuánto queda visible un aviso; se pausa mientras el puntero o el foco están encima. */
export const TOAST_MS = 5000;

const ToastView = ({ item, onClose }: { item: ToastItem; onClose: () => void }) => {
  const [paused, setPaused] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => {
    if (paused) return undefined;
    timer.current = setTimeout(onClose, TOAST_MS);
    return () => clearTimeout(timer.current);
  }, [paused, onClose]);
  return (
    <div className={cn('toast', `toast-${item.kind}`)} role={item.kind === 'error' ? 'alert' : 'status'}
         onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
         onFocus={() => setPaused(true)} onBlur={() => setPaused(false)}>
      <span className="toast-icon" aria-hidden="true">{item.kind === 'error' ? '!' : item.kind === 'success' ? '✓' : 'i'}</span>
      <span className="toast-message">{item.message}</span>
      <button type="button" className="toast-close" aria-label="Cerrar aviso" onClick={onClose}>×</button>
    </div>
  );
};

/**
 * Avisos emergentes para acciones que terminan sin cambiar de pantalla (guardado, acreditado, sesión vencida).
 * Los éxitos se anuncian con role="status" y los errores con role="alert". La validación de formularios no va acá:
 * se muestra junto al campo.
 */
export const ToastProvider = ({ children }: { children: ReactNode }) => {
  const [items, setItems] = useState<ToastItem[]>([]);
  const next = useRef(1);
  const push = useCallback<Push>((kind, message) => {
    setItems((current) => [...current.slice(-2), { id: next.current++, kind, message }]); // como máximo 3 a la vez
  }, []);
  const close = useCallback((id: number) => setItems((current) => current.filter((t) => t.id !== id)), []);
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="toast-region">
        {items.map((item) => <ToastView key={item.id} item={item} onClose={() => close(item.id)} />)}
      </div>
    </ToastContext.Provider>
  );
};

/** `toast.success('Perfil guardado')`, `toast.error(...)`, `toast.info(...)`. */
export const useToast = () => {
  const push = useContext(ToastContext);
  return useMemo(() => ({
    success: (message: string) => push('success', message),
    error: (message: string) => push('error', message),
    info: (message: string) => push('info', message),
  }), [push]);
};
