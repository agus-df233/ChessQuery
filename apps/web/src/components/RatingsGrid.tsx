import type { Ratings } from '../api/types';
import { RATING_GROUPS } from '../lib/ratings';

/** Grilla de ratings por fuente. Solo muestra las modalidades con valor. */
export const RatingsGrid = ({ ratings }: { ratings: Ratings }) => {
  const groups = RATING_GROUPS
    .map((g) => ({ ...g, items: g.items.filter((i) => ratings[i.key] != null) }))
    .filter((g) => g.items.length > 0);
  if (groups.length === 0) return <p className="cq-muted">Todavía no hay ratings registrados.</p>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {groups.map((g) => (
        <section key={g.source} aria-label={`Ratings ${g.source}`}>
          <div className="cq-muted" style={{ marginBottom: 6 }}>{g.source}</div>
          <div className="cq-ratings">
            {g.items.map((i) => (
              <div key={i.key} className="cq-rating">
                <div className="label">{i.label}</div>
                <div className="value">{ratings[i.key]}</div>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
};
