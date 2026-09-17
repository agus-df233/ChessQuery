import { HTMLAttributes } from 'react';
import { cn } from '../utils/cn';

type Variant = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'gold';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: Variant;
  dot?: boolean;
  pulse?: boolean;
}

const VARIANT_CLASS: Record<Variant, string> = {
  success: 'badge-green',
  warning: 'badge-yellow',
  danger: 'badge-red',
  info: 'badge-blue',
  neutral: 'badge-muted',
  gold: 'badge-gold',
};

const DOT_COLOR: Record<Variant, string> = {
  success: 'var(--accent)',
  warning: 'var(--yellow)',
  danger: 'var(--red)',
  info: 'var(--blue)',
  neutral: 'var(--text-muted)',
  gold: 'var(--gold)',
};

export const Badge = ({ variant = 'neutral', dot, pulse, className, children, ...rest }: BadgeProps) => (
  <span className={cn('badge', VARIANT_CLASS[variant], pulse && 'pulse', className)} {...rest}>
    {dot && (
      <span
        style={{
          width: 6,
          height: 6,
          borderRadius: '50%',
          background: DOT_COLOR[variant],
          display: 'inline-block',
        }}
      />
    )}
    {children}
  </span>
);
