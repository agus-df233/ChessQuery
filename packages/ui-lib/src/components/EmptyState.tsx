import { ReactNode } from 'react';

export interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}

export const EmptyState = ({ icon = '♟', title, description, action }: EmptyStateProps) => (
  <div
    style={{
      textAlign: 'center',
      padding: '48px 24px',
      color: 'var(--text-muted)',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 10,
    }}
  >
    <div style={{ fontSize: 42, opacity: 0.6 }}>{icon}</div>
    <div
      style={{
        fontFamily: "'Space Grotesk', system-ui, sans-serif",
        fontWeight: 600,
        fontSize: 16,
        color: 'var(--text)',
      }}
    >
      {title}
    </div>
    {description && <div style={{ fontSize: 13, maxWidth: 360 }}>{description}</div>}
    {action && <div style={{ marginTop: 8 }}>{action}</div>}
  </div>
);
