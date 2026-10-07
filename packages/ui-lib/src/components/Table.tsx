import { ReactNode } from 'react';

export interface TableColumn<T> {
  key: string;
  header: ReactNode;
  width?: number | string;
  align?: 'left' | 'center' | 'right';
  render: (row: T, index: number) => ReactNode;
}

export interface TableProps<T> {
  columns: TableColumn<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string | number;
  emptyMessage?: string;
  /** O6-03: fila resaltada (p.ej. mesa activa para atajos de teclado). */
  activeRowKey?: string | number;
  /**
   * Nombre de la tabla. El contenedor con desplazamiento horizontal (pantallas angostas) es una región enfocable con
   * este nombre, para poder desplazarla con el teclado (WCAG 2.1.1).
   */
  label?: string;

  // Pagination (optional, server-side)
  page?: number;
  totalPages?: number;
  onPageChange?: (page: number) => void;
}

export function Table<T>({
  columns,
  rows,
  rowKey,
  emptyMessage = 'Sin resultados',
  activeRowKey,
  label = 'Tabla',
  page,
  totalPages,
  onPageChange,
}: TableProps<T>) {
  return (
    <div>
      <div style={{ overflowX: 'auto' }} tabIndex={0} role="region" aria-label={label}>
        <table>
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c.key} style={{ width: c.width, textAlign: c.align ?? 'left' }}>
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={columns.length} style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '32px 16px' }}>
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              rows.map((row, i) => {
                const key = rowKey(row, i);
                const active = activeRowKey != null && key === activeRowKey;
                return (
                  <tr
                    key={key}
                    data-active={active || undefined}
                    style={active ? {
                      background: 'rgba(106,191,116,0.1)',
                      boxShadow: 'inset 3px 0 0 var(--accent, #6abf74)',
                    } : undefined}
                  >
                    {columns.map((c) => (
                      <td key={c.key} style={{ textAlign: c.align ?? 'left' }}>
                        {c.render(row, i)}
                      </td>
                    ))}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {typeof page === 'number' && typeof totalPages === 'number' && totalPages > 1 && onPageChange && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            gap: 8,
            padding: '12px 16px',
            borderTop: '1px solid var(--border)',
          }}
        >
          <button
            type="button"
            className="btn btn-ghost"
            disabled={page <= 0}
            onClick={() => onPageChange(page - 1)}
          >
            ← Anterior
          </button>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            Página {page + 1} de {totalPages}
          </span>
          <button
            type="button"
            className="btn btn-ghost"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
          >
            Siguiente →
          </button>
        </div>
      )}
    </div>
  );
}
