import { SelectHTMLAttributes, forwardRef, useId } from 'react';
import { cn } from '../utils/cn';

export interface SelectOption {
  label: string;
  value: string;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  options: SelectOption[];
  placeholder?: string;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, error, options, placeholder, className, id, style, 'aria-label': ariaLabel, ...rest }, ref) => {
    const reactId = useId();
    const selectId = id || (label ? `select-${label.replace(/\s+/g, '-').toLowerCase()}` : `select-${reactId}`);
    const msgId = error ? `${selectId}-msg` : undefined;
    return (
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {label && (
          <label htmlFor={selectId} className="label">
            {label}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          className={cn('input', className)}
          aria-label={!label ? ariaLabel : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={msgId}
          style={{ borderColor: error ? 'var(--red)' : undefined, ...style }}
          {...rest}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        {error && (
          <span id={msgId} role="alert" style={{ fontSize: 12, color: 'var(--red)', marginTop: 4 }}>
            {error}
          </span>
        )}
      </div>
    );
  },
);
Select.displayName = 'Select';
