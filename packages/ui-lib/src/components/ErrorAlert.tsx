import { ReactNode } from 'react';

export interface ErrorAlertProps {
  title?: string;
  message?: ReactNode;
  onRetry?: () => void;
}

export const ErrorAlert = ({ title = 'Ocurrió un error', message, onRetry }: ErrorAlertProps) => (
  <div
    style={{
      border: '1px solid oklch(62% 0.22 20 / 0.3)',
      background: 'oklch(62% 0.22 20 / 0.08)',
      borderRadius: 10,
      padding: '14px 16px',
      color: 'var(--text)',
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
    }}
    role="alert"
  >
    <div
      style={{
        fontFamily: "'Space Grotesk', system-ui, sans-serif",
        fontWeight: 600,
        fontSize: 13,
        color: 'var(--red)',
        display: 'flex',
        alignItems: 'center',
        gap: 6,
      }}
    >
      <span>⚠</span> {title}
    </div>
    {message && <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>{message}</div>}
    {onRetry && (
      <button type="button" className="btn btn-ghost" onClick={onRetry} style={{ alignSelf: 'flex-start', marginTop: 4 }}>
        ↻ Reintentar
      </button>
    )}
  </div>
);
