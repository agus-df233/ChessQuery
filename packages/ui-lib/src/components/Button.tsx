import { ButtonHTMLAttributes, forwardRef } from 'react';
import { cn } from '../utils/cn';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';
type Size = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  fullWidth?: boolean;
}

const SIZE_STYLES: Record<Size, { height: string; padding: string; fontSize: string }> = {
  sm: { height: '28px', padding: '0 10px', fontSize: '12px' },
  md: { height: '36px', padding: '0 14px', fontSize: '13px' },
  lg: { height: '44px', padding: '0 20px', fontSize: '14px' },
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', loading, fullWidth, className, children, disabled, style, type, ...rest }, ref) => {
    const sz = SIZE_STYLES[size];
    return (
      <button
        ref={ref}
        type={type ?? 'button'}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        className={cn('btn', `btn-${variant}`, className)}
        style={{
          height: sz.height,
          padding: sz.padding,
          fontSize: sz.fontSize,
          width: fullWidth ? '100%' : undefined,
          justifyContent: 'center',
          ...style,
        }}
        {...rest}
      >
        {loading && (
          <span className="spin" aria-hidden="true">
            ⟳
          </span>
        )}
        {children}
      </button>
    );
  },
);
Button.displayName = 'Button';
