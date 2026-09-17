import { ReactNode } from 'react';

export interface CheckboxProps {
  checked: boolean;
  onChange: () => void;
  children: ReactNode;
  /** Alinea la caja con la primera línea del texto (útil con labels largos). */
  alignStart?: boolean;
}

/**
 * Checkbox accesible: input nativo real superpuesto (operable por teclado:
 * Tab + Espacio) sobre una caja visual con check. Nombre accesible vía la
 * `<label>` que lo envuelve; el foco por teclado se ve en la caja
 * (`:focus-visible + span`). Usa tokens del tema (funciona en ambas apps).
 */
export const Checkbox = ({ checked, onChange, children, alignStart }: CheckboxProps) => (
  <label
    style={{
      display: 'flex',
      alignItems: alignStart ? 'flex-start' : 'center',
      gap: 10,
      cursor: 'pointer',
      userSelect: 'none',
    }}
  >
    <span style={{ position: 'relative', width: 18, height: 18, flexShrink: 0, marginTop: alignStart ? 1 : 0 }}>
      <input
        type="checkbox"
        className="cq-cbx-input"
        checked={checked}
        onChange={onChange}
        style={{ position: 'absolute', inset: 0, width: 18, height: 18, margin: 0, opacity: 0, cursor: 'pointer' }}
      />
      <span
        aria-hidden="true"
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: 5,
          border: `1.5px solid ${checked ? 'var(--cq-accent, #6abf74)' : 'var(--cq-border, #2a2d27)'}`,
          background: checked ? 'var(--cq-accent, #6abf74)' : 'transparent',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          transition: 'all 0.2s',
        }}
      >
        {checked && (
          <svg width="10" height="8" viewBox="0 0 10 8" fill="none">
            <path d="M1 4L4 7L9 1" stroke="#0e100d" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        )}
      </span>
    </span>
    <span style={{ fontSize: 13, color: 'var(--cq-text-dim, #9a9c8c)', lineHeight: 1.5 }}>{children}</span>
    <style>{`.cq-cbx-input:focus-visible + span { outline: 2px solid var(--cq-accent, #6abf74); outline-offset: 2px; border-radius: 6px; }`}</style>
  </label>
);
