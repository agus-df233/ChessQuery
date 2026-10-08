import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { usersApi } from '../api/users';
import type { Profile, RatingType } from '../api/types';
import { RATING_GROUPS, displayName } from '../lib/ratings';
import { RatingsGrid } from '../components/RatingsGrid';
import { RatingChart } from '../components/RatingChart';
import { StatusMessage } from '../components/StatusMessage';
import { ClaimSuggestions, FederationCard } from '../components/FederationCard';
import { WelcomeWizard } from '../components/welcome/WelcomeWizard';

const REFRESH_AFTER_SYNC_MS = [4000, 12000];

const CHART_OPTIONS = RATING_GROUPS.flatMap((g) => g.items.map((i) => ({ type: i.type, label: `${g.source} · ${i.label}` })));

const MyCard = ({ profile: p, organizer }: { profile: Profile; organizer: boolean }) => (
  <Card header="Mi ficha">
    <p style={{ margin: 0, fontWeight: 600 }}>{displayName(p)}</p>
    <p className="cq-muted">{[p.club?.name, p.region, p.country?.name].filter(Boolean).join(' · ') || 'Completa tu perfil'}</p>
    <p className="cq-muted">Categoría {p.ageCategory.replace('SUB_', 'Sub ')}</p>
    <div className="cq-actions">
      <Link to="/app/perfil"><Button variant="secondary" size="sm">Editar perfil</Button></Link>
      {!organizer && <Link to="/club"><Button size="sm">Crear mi club</Button></Link>}
    </div>
  </Card>
);

/** Cuentas de Lichess/Chess.com vinculadas y el botón para traer sus ratings actuales. */
const ExternalAccountsCard = ({ profile: p }: { profile: Profile }) => {
  const qc = useQueryClient();
  // Los ratings los trae el ETL (Lambda) y llegan en segundos: se vuelve a consultar el perfil un par de veces
  const sync = useMutation({
    mutationFn: usersApi.syncExternalRatings,
    onSuccess: () => {
      for (const ms of REFRESH_AFTER_SYNC_MS) {
        setTimeout(() => { void qc.invalidateQueries({ queryKey: ['me'] }); void qc.invalidateQueries({ queryKey: ['my-history'] }); }, ms);
      }
    },
  });
  if (!p.lichessUsername && !p.chesscomUsername) {
    return (
      <Card header="Cuentas externas">
        <p className="cq-muted">Vincula tu usuario de Lichess o Chess.com desde <Link to="/app/perfil">tu perfil</Link>.</p>
      </Card>
    );
  }
  return (
    <Card header="Cuentas externas">
      <p className="cq-muted">Lichess: {p.lichessUsername ?? '—'} · Chess.com: {p.chesscomUsername ?? '—'}</p>
      <div className="cq-actions">
        <Button size="sm" onClick={() => sync.mutate()} loading={sync.isPending}>Actualizar ratings</Button>
        <StatusMessage error={sync.error} success={sync.isSuccess ? 'Pedido enviado: tus ratings se actualizan en unos segundos' : null} />
      </div>
    </Card>
  );
};

/** Gráfico de 12 meses de la modalidad elegida. */
const ProgressCard = () => {
  const [type, setType] = useState<RatingType>('NATIONAL');
  const history = useQuery({ queryKey: ['my-history', type], queryFn: () => usersApi.myRatingHistory(type, 12) });
  const label = CHART_OPTIONS.find((o) => o.type === type)?.label ?? type;
  return (
    <Card header={
      <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        Progreso (12 meses)
        <select className="cq-input" value={type} onChange={(e) => setType(e.target.value as RatingType)} aria-label="Modalidad del gráfico">
          {CHART_OPTIONS.map((o) => <option key={o.type} value={o.type}>{o.label}</option>)}
        </select>
      </label>
    }>
      {history.isLoading ? <Skeleton height={160} /> : <RatingChart points={history.data ?? []} label={label} />}
    </Card>
  );
};

/** El asistente se muestra a quien aún no vio la bienvenida y queda visible hasta que lo cierre (siguientes pasos). */
const WelcomeSlot = ({ profile }: { profile: Profile }) => {
  const [show, setShow] = useState(profile.welcomedAt == null);
  return show ? <WelcomeWizard onClose={() => setShow(false)} /> : null;
};

/** Inicio del jugador: quién soy, mis ratings, mi progreso y las cuentas externas vinculadas. */
export const Dashboard = () => {
  const me = useMe();
  if (me.isLoading) return <Skeleton height={120} />;
  if (me.error || !me.data) return <ErrorAlert message="No pudimos cargar tu perfil" onRetry={() => void me.refetch()} />;
  const p = me.data.profile;
  return (
    <div className="cq-page">
      <h1>Hola, {p.displayName ?? p.firstName}</h1>
      <WelcomeSlot profile={p} />
      <ClaimSuggestions />
      <div className="cq-grid">
        <MyCard profile={p} organizer={me.data.organizer} />
        <FederationCard profile={p} />
        <ExternalAccountsCard profile={p} />
      </div>
      <Card header="Mis ratings"><RatingsGrid ratings={p.ratings} /></Card>
      <ProgressCard />
    </div>
  );
};
