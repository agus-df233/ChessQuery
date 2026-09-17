import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { usersApi } from '../api/users';
import type { RatingType } from '../api/types';
import { RATING_GROUPS, displayName } from '../lib/ratings';
import { RatingsGrid } from '../components/RatingsGrid';
import { RatingChart } from '../components/RatingChart';
import { StatusMessage } from '../components/StatusMessage';

/** Inicio del jugador: quién soy, mis ratings, mi progreso y las cuentas externas vinculadas. */
export const Dashboard = () => {
  const qc = useQueryClient();
  const me = useMe();
  const [type, setType] = useState<RatingType>('NATIONAL');
  const history = useQuery({ queryKey: ['my-history', type], queryFn: () => usersApi.myRatingHistory(type, 12) });
  const sync = useMutation({
    mutationFn: usersApi.syncExternalRatings,
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['me'] }); void qc.invalidateQueries({ queryKey: ['my-history'] }); },
  });

  if (me.isLoading) return <Skeleton height={120} />;
  if (me.error || !me.data) return <ErrorAlert message="No pudimos cargar tu perfil" onRetry={() => void me.refetch()} />;
  const p = me.data.profile;
  const linked = p.lichessUsername || p.chesscomUsername;
  const options = RATING_GROUPS.flatMap((g) => g.items.map((i) => ({ type: i.type, label: `${g.source} · ${i.label}` })));

  return (
    <div className="cq-page">
      <h1>Hola, {p.displayName ?? p.firstName}</h1>
      <div className="cq-grid">
        <Card header="Mi ficha">
          <p style={{ margin: 0, fontWeight: 600 }}>{displayName(p)}</p>
          <p className="cq-muted">{[p.club?.name, p.region, p.country?.name].filter(Boolean).join(' · ') || 'Completa tu perfil'}</p>
          <p className="cq-muted">Categoría {p.ageCategory.replace('SUB_', 'Sub ')}</p>
          <div className="cq-actions">
            <Link to="/app/perfil"><Button variant="secondary" size="sm">Editar perfil</Button></Link>
            {!me.data.organizer && <Link to="/club"><Button size="sm">Crear mi club</Button></Link>}
          </div>
        </Card>
        <Card header="Cuentas externas">
          {linked ? (
            <>
              <p className="cq-muted">Lichess: {p.lichessUsername ?? '—'} · Chess.com: {p.chesscomUsername ?? '—'}</p>
              <div className="cq-actions">
                <Button size="sm" onClick={() => sync.mutate()} loading={sync.isPending}>Actualizar ratings</Button>
                <StatusMessage error={sync.error} success={sync.isSuccess ? 'Ratings actualizados' : null} />
              </div>
            </>
          ) : (
            <p className="cq-muted">Vincula tu usuario de Lichess o Chess.com desde <Link to="/app/perfil">tu perfil</Link>.</p>
          )}
        </Card>
      </div>
      <Card header="Mis ratings"><RatingsGrid ratings={p.ratings} /></Card>
      <Card header={
        <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          Progreso (12 meses)
          <select className="cq-input" value={type} onChange={(e) => setType(e.target.value as RatingType)} aria-label="Modalidad del gráfico">
            {options.map((o) => <option key={o.type} value={o.type}>{o.label}</option>)}
          </select>
        </label>
      }>
        {history.isLoading ? <Skeleton height={160} /> : <RatingChart points={history.data ?? []} label={options.find((o) => o.type === type)?.label ?? type} />}
      </Card>
    </div>
  );
};
