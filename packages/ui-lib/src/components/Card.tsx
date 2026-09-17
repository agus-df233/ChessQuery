import { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../utils/cn';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  header?: ReactNode;
  footer?: ReactNode;
  padded?: boolean;
  hover?: boolean;
}

export const Card = ({ header, footer, padded = true, hover, className, children, style, ...rest }: CardProps) => (
  <div className={cn('card', hover && 'card-hover', className)} style={style} {...rest}>
    {header && (
      <div
        style={{
          padding: '12px 18px',
          borderBottom: '1px solid var(--border)',
          fontFamily: "'Space Grotesk', system-ui, sans-serif",
          fontWeight: 600,
          fontSize: 14,
        }}
      >
        {header}
      </div>
    )}
    <div style={{ padding: padded ? '16px 18px' : 0 }}>{children}</div>
    {footer && (
      <div
        style={{
          padding: '10px 18px',
          borderTop: '1px solid var(--border)',
          fontSize: 12,
          color: 'var(--text-muted)',
        }}
      >
        {footer}
      </div>
    )}
  </div>
);
