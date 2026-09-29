import { FormEvent, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Skeleton, Table, type TableColumn } from '@chessquery/ui-lib';
import { usersApi } from '../api/users';
import type { SearchResult } from '../api/types';
import { displayName } from '../lib/ratings';
import { RatingsGrid } from '../components/RatingsGrid';
import { RatingChart } from '../components/RatingChart';
import { FriendButton } from '../components/FriendButton';
import { ChallengeButton } from '../components/game/ChallengeButton';

/** Búsqueda de jugadores por nombre (tolerante a errores de tipeo), RUT o FIDE id. */
export const PlayerSearch = () => {
  const [input, setInput] = useState('');
  const [q, setQ] = useState('');
  const results = useQuery({ queryKey: ['search', q], queryFn: () => usersApi.search(q), enabled: q.length >= 2 });
  const columns: TableColumn<SearchResult>[] = [
    { key: 'name', header: 'Jugador', render: (r) => <Link to={`/app/jugadores/${r.id}`}>{displayName(r)}</Link> },
    { key: 'club', header: 'Club', render: (r) => r.clubName ?? '—' },
    { key: 'nat', header: 'ELO Nac.', align: 'right', render: (r) => r.eloNational ?? '—' },
    { key: 'fide', header: 'FIDE', align: 'right', render: (r) => r.eloFideStandard ?? '—' },
  ];
  const onSubmit = (e: FormEvent) => { e.preventDefault(); setQ(input.trim()); };
  return (
    <div className="cq-page">
      <h1>Jugadores</h1>
      <form onSubmit={onSubmit} className="cq-actions">
        <input className="cq-input" aria-label="Buscar jugador" placeholder="Nombre, RUT o FIDE id" value={input} onChange={(e) => setInput(e.target.value)} style={{ flex: 1, minWidth: 220 }} />
        <Button type="submit" disabled={input.trim().length < 2}>Buscar</Button>
      </form>
      {results.isLoading && <Skeleton height={120} />}
      {results.error && <ErrorAlert message="No se pudo buscar" onRetry={() => void results.refetch()} />}
      {results.data && (results.data.length === 0
        ? <EmptyState title="Sin resultados" description="Prueba con otro nombre o revisa el RUT." />
        : <Card padded={false}><Table columns={columns} rows={results.data} rowKey={(r) => r.id} /></Card>)}
    </div>
  );
};

/** Perfil público de otro jugador (sin datos personales) con botón de amistad y su curva. */
export const PlayerDetail = () => {
  const id = Number(useParams().id);
  const profile = useQuery({ queryKey: ['public-profile', id], queryFn: () => usersApi.publicProfile(id) });
  const history = useQuery({ queryKey: ['history', id], queryFn: () => usersApi.ratingHistory(id, 'NATIONAL', 12) });
  if (profile.isLoading) return <Skeleton height={200} />;
  if (profile.error || !profile.data) return <ErrorAlert message="Jugador no encontrado" />;
  const p = profile.data;
  return (
    <div className="cq-page">
      <h1>{displayName(p)}</h1>
      <div className="cq-actions">
        <Badge variant="info">{p.ageCategory.replace('SUB_', 'Sub ')}</Badge>
        {p.club && <Badge>{p.club.name}</Badge>}
        {p.country && <Badge>{p.country.name}</Badge>}
        {p.fideId && <Badge variant="gold">FIDE {p.fideId}</Badge>}
      </div>
      <div className="cq-actions">
        <FriendButton otherId={id} />
        <ChallengeButton opponentId={id} opponentName={`${p.firstName} ${p.lastName}`} />
      </div>
      <Card header="Ratings"><RatingsGrid ratings={p.ratings} /></Card>
      <Card header="ELO nacional, últimos 12 meses">
        {history.isLoading ? <Skeleton height={160} /> : <RatingChart points={history.data ?? []} label="ELO Nacional" />}
      </Card>
    </div>
  );
};
