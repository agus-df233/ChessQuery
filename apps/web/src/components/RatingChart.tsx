import type { RatingPoint } from '../api/types';

/**
 * Curva de rating como SVG inline (sin librería de gráficos, por presupuesto de bundle).
 * Escala lineal en X por índice y en Y por valor; con un solo punto dibuja una línea plana.
 */
export const RatingChart = ({ points, label }: { points: RatingPoint[]; label: string }) => {
  if (points.length === 0) return <p className="cq-muted">Sin historial en el período.</p>;
  const w = 600, h = 160, pad = 24;
  const values = points.map((p) => p.rating);
  const min = Math.min(...values), max = Math.max(...values);
  const span = max - min || 1;
  const x = (i: number) => pad + (i * (w - 2 * pad)) / Math.max(points.length - 1, 1);
  const y = (v: number) => h - pad - ((v - min) * (h - 2 * pad)) / span;
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.rating).toFixed(1)}`).join(' ');
  const first = points[0], last = points[points.length - 1];
  const summary = `${label}: de ${first.rating} a ${last.rating} en ${points.length} registros`;
  return (
    <figure style={{ margin: 0 }}>
      <svg viewBox={`0 0 ${w} ${h}`} role="img" aria-label={summary} style={{ width: '100%', height: 'auto' }}>
        <path d={path} fill="none" stroke="var(--accent)" strokeWidth={2} />
        {points.map((p, i) => (
          <circle key={p.recordedAt + i} cx={x(i)} cy={y(p.rating)} r={3} fill="var(--accent)">
            <title>{`${new Date(p.recordedAt).toLocaleDateString('es-CL')}: ${p.rating}`}</title>
          </circle>
        ))}
        <text x={pad} y={h - 6} fontSize={11} fill="var(--text-dim)">{min}</text>
        <text x={pad} y={14} fontSize={11} fill="var(--text-dim)">{max}</text>
      </svg>
      <figcaption className="cq-muted">{summary}</figcaption>
    </figure>
  );
};
