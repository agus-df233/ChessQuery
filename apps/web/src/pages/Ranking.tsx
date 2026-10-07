import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card, ErrorAlert, Skeleton, Table, type TableColumn } from '@chessquery/ui-lib';
import { publicApi, usersApi } from '../api/users';
import type { RankedType, RankingEntry } from '../api/types';
import { AGE_CATEGORIES, RANKED_TYPES, categoryLabel, displayName } from '../lib/ratings';

/**
 * Ranking por ELO nacional o FIDE, filtrable por categoría de edad y región. En modo público
 * (sin sesión, `/ranking`) usa /api/public y no enlaza a perfiles: es la vista para compartir.
 */
export const Ranking = ({ publicView = false }: { publicView?: boolean }) => {
  const [type, setType] = useState<RankedType>(publicView ? 'FIDE_STANDARD' : 'NATIONAL');
  const [category, setCategory] = useState('');
  const [region, setRegion] = useState('');
  const api = publicView ? publicApi : usersApi;
  const ranking = useQuery({
    queryKey: ['ranking', publicView, type, category, region],
    queryFn: () => api.ranking(category, region, 100, type),
  });
  const typeInfo = RANKED_TYPES.find((t) => t.type === type) ?? RANKED_TYPES[0];
  const columns: TableColumn<RankingEntry>[] = [
    { key: 'pos', header: '#', width: 48, render: (r) => r.position },
    { key: 'name', header: 'Jugador', render: (r) => publicView
        ? displayName(r)
        : <Link to={`/app/jugadores/${r.playerId}`}>{displayName(r)}</Link> },
    { key: 'club', header: 'Club', render: (r) => r.clubName ?? '—' },
    { key: 'cat', header: 'Categoría', render: (r) => categoryLabel(r.ageCategory) },
    { key: 'elo', header: typeInfo.short, align: 'right', render: (r) => r.rating ?? '—' },
  ];
  return (
    <div className="cq-page">
      <h1>{publicView ? 'Ranking de Chile' : 'Ranking nacional'}</h1>
      <div className="cq-form">
        <label>Rating
          <select value={type} onChange={(e) => setType(e.target.value as RankedType)}>
            {RANKED_TYPES.map((t) => <option key={t.type} value={t.type}>{t.label}</option>)}
          </select>
        </label>
        <label>Categoría
          <select value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">Todas</option>
            {AGE_CATEGORIES.map((c) => <option key={c} value={c}>{categoryLabel(c)}</option>)}
          </select>
        </label>
        <label>Región<input value={region} onChange={(e) => setRegion(e.target.value)} placeholder="Todas" /></label>
      </div>
      {ranking.isLoading && <Skeleton height={200} />}
      {ranking.error && <ErrorAlert message="No se pudo cargar el ranking" onRetry={() => void ranking.refetch()} />}
      {ranking.data && <Card padded={false}><Table label="Ranking" columns={columns} rows={ranking.data} rowKey={(r) => r.playerId} emptyMessage={`Nadie con rating ${typeInfo.label.toLowerCase()} en ese filtro.`} /></Card>}
    </div>
  );
};
