import { memo, useMemo, useState } from 'react';
import { Table, TableColumn } from './Table';

export interface StandingEntry {
  rank: number;
  playerId: number | string;
  playerName: string;
  points: number;
  buchholz?: number;
  sonneborn?: number;
  gamesPlayed?: number;
}

export interface StandingsTableProps {
  entries: StandingEntry[];
  showTiebreakers?: boolean;
  /** P3-02: filas visibles antes del "Mostrar más" (default 25). */
  pageSize?: number;
}

const baseColumns: TableColumn<StandingEntry>[] = [
  {
    key: 'rank',
    header: '#',
    width: 44,
    render: (r) => (
      <span
        style={{
          fontFamily: "'Space Grotesk', system-ui, sans-serif",
          fontWeight: 700,
          color: r.rank <= 3 ? 'var(--accent)' : 'var(--text-muted)',
        }}
      >
        {r.rank}
      </span>
    ),
  },
  {
    key: 'name',
    header: 'Jugador',
    render: (r) => (
      <span style={{ fontFamily: "'Space Grotesk', system-ui, sans-serif", fontWeight: 600 }}>
        {r.playerName}
      </span>
    ),
  },
  {
    key: 'points',
    header: 'Puntos',
    align: 'right',
    render: (r) => (
      <span
        style={{
          fontFamily: "'Space Grotesk', system-ui, sans-serif",
          fontWeight: 700,
          color: 'var(--accent)',
        }}
      >
        {r.points.toFixed(1)}
      </span>
    ),
  },
];

const tiebreakerColumns: TableColumn<StandingEntry>[] = [
  {
    key: 'buchholz',
    header: 'Buchholz',
    align: 'right',
    render: (r) => (
      <span style={{ color: 'var(--text-muted)', fontFamily: 'monospace', fontSize: 12 }}>
        {r.buchholz != null ? r.buchholz.toFixed(1) : '—'}
      </span>
    ),
  },
  {
    key: 'sonneborn',
    header: 'Son.-Berger',
    align: 'right',
    render: (r) => (
      <span style={{ color: 'var(--text-muted)', fontFamily: 'monospace', fontSize: 12 }}>
        {r.sonneborn != null ? r.sonneborn.toFixed(1) : '—'}
      </span>
    ),
  },
];

/**
 * P3-02: memoizado (los standings llegan por Realtime con frecuencia y este
 * componente vive dentro de vistas grandes) y con revelado progresivo para
 * torneos con muchas filas: se muestran `pageSize` posiciones y un botón
 * "Mostrar más" — el DOM no crece con el torneo completo de entrada.
 */
export const StandingsTable = memo(
  ({ entries, showTiebreakers = true, pageSize = 25 }: StandingsTableProps) => {
    const [visibleCount, setVisibleCount] = useState(pageSize);

    const columns = useMemo(
      () => (showTiebreakers ? [...baseColumns, ...tiebreakerColumns] : baseColumns),
      [showTiebreakers],
    );

    const visibleEntries = useMemo(
      () => (entries.length > visibleCount ? entries.slice(0, visibleCount) : entries),
      [entries, visibleCount],
    );
    const remaining = entries.length - visibleEntries.length;

    return (
      <div>
        <Table
          columns={columns}
          rows={visibleEntries}
          rowKey={(e) => e.playerId}
          emptyMessage="Aún no hay posiciones registradas"
        />
        {remaining > 0 && (
          <button
            type="button"
            onClick={() => setVisibleCount((c) => c + pageSize)}
            style={{
              marginTop: 10,
              padding: '8px 14px',
              borderRadius: 8,
              border: '1px solid var(--border, #2a2d27)',
              background: 'transparent',
              color: 'var(--text, #e8ead4)',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              width: '100%',
            }}
          >
            Mostrar {Math.min(remaining, pageSize)} más ({remaining} restantes)
          </button>
        )}
      </div>
    );
  },
);
StandingsTable.displayName = 'StandingsTable';
