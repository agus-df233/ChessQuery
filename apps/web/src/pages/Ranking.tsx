import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card, ErrorAlert, Skeleton, Table, type TableColumn } from '@chessquery/ui-lib';
import { usersApi } from '../api/users';
import type { RankingEntry } from '../api/types';
import { AGE_CATEGORIES, categoryLabel, displayName } from '../lib/ratings';

/** Ranking nacional por ELO nacional, filtrable por categoría de edad y región. */
export const Ranking = () => {
  const [category, setCategory] = useState('');
  const [region, setRegion] = useState('');
  const ranking = useQuery({ queryKey: ['ranking', category, region], queryFn: () => usersApi.ranking(category, region, 100) });
  const columns: TableColumn<RankingEntry>[] = [
    { key: 'pos', header: '#', width: 48, render: (r) => r.position },
    { key: 'name', header: 'Jugador', render: (r) => <Link to={`/app/jugadores/${r.playerId}`}>{displayName(r)}</Link> },
    { key: 'club', header: 'Club', render: (r) => r.clubName ?? '—' },
    { key: 'cat', header: 'Categoría', render: (r) => categoryLabel(r.ageCategory) },
    { key: 'elo', header: 'ELO Nac.', align: 'right', render: (r) => r.eloNational ?? '—' },
  ];
  return (
    <div className="cq-page">
      <h1>Ranking nacional</h1>
      <div className="cq-form">
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
      {ranking.data && <Card padded={false}><Table columns={columns} rows={ranking.data} rowKey={(r) => r.playerId} emptyMessage="Nadie con ELO nacional en ese filtro." /></Card>}
    </div>
  );
};
