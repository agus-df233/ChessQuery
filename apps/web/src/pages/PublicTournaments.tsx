import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Card, EmptyState, Skeleton } from '@chessquery/ui-lib';
import { publicTournamentsApi } from '../api/tournaments';
import { QrCode } from '../components/QrCode';
import type { FederationTournament, TournamentView } from '../api/tournamentTypes';
import { STATUS_BADGE, STATUS_LABEL, formatDate, summary } from '../components/tournament/labels';
import { TournamentDetailView } from '../components/tournament/TournamentDetailView';

/** Tarjeta de un torneo de club con enlace a su detalle ({@code base} = /torneos o /app/torneos). */
export const TournamentItem = ({ t, base, children }: { t: TournamentView; base: string; children?: React.ReactNode }) => (
  <li className="cq-tournament-item">
    <div>
      <Link to={`${base}/${t.id}`} style={{ fontWeight: 600 }}>{t.name}</Link>{' '}
      <Badge variant={STATUS_BADGE[t.status]}>{STATUS_LABEL[t.status]}</Badge>
      <div className="cq-muted">{summary(t)}</div>
    </div>
    {children}
  </li>
);

export const TournamentList = ({ items, base, empty, action }: {
  items: TournamentView[] | undefined; base: string; empty: string; action?: (t: TournamentView) => React.ReactNode;
}) => {
  if (!items) return <Skeleton height={80} />;
  if (items.length === 0) return <EmptyState title={empty} />;
  return <ul className="cq-list">{items.map((t) => <TournamentItem key={t.id} t={t} base={base}>{action?.(t)}</TournamentItem>)}</ul>;
};

const fedLine = (f: FederationTournament) =>
  [formatDate(f.startDate), f.city, f.type, f.rounds && `${f.rounds} rondas`, f.timeControl].filter(Boolean).join(' · ');

/** Calendario de la Federación Chilena de Ajedrez (lo trae el ETL; solo datos del evento). */
export const FederationCalendar = () => {
  const calendar = useQuery({ queryKey: ['federation-calendar'], queryFn: publicTournamentsApi.calendar });
  return (
    <Card header="Calendario de la Federación">
      {calendar.isLoading ? <Skeleton height={80} /> : (calendar.data ?? []).length === 0
        ? <p className="cq-muted">Sin torneos federados próximos.</p>
        : (
          <ul className="cq-list">
            {calendar.data!.map((f) => (
              <li key={f.federationTournamentId}>
                <strong>{f.title}</strong>{' '}
                {f.ratedFide && <Badge variant="gold">FIDE</Badge>}{' '}
                {f.ratedNational && <Badge>Nacional</Badge>}
                <div className="cq-muted">{fedLine(f)}</div>
              </li>
            ))}
          </ul>
        )}
    </Card>
  );
};

/** /torneos: torneos de los clubes y calendario federativo, sin login. */
export const PublicTournaments = () => {
  const list = useQuery({ queryKey: ['public-tournaments'], queryFn: () => publicTournamentsApi.list() });
  return (
    <main className="cq-page cq-public">
      <p><Link to="/">♔ ChessQuery</Link></p>
      <h1>Torneos</h1>
      <Card header="Torneos de los clubes"><TournamentList items={list.data} base="/torneos" empty="Aún no hay torneos publicados" /></Card>
      <FederationCalendar />
    </main>
  );
};

/** /torneos/:id: pantalla pública para la sala (QR): tabla, rondas e inscritos, se refresca sola. */
export const PublicTournamentDetail = () => {
  const { id } = useParams();
  return (
    <main className="cq-public">
      <p style={{ padding: '0 16px' }}><Link to="/torneos">← Torneos</Link></p>
      <TournamentDetailView id={Number(id)} actions={(d) => d.tournament.status === 'OPEN' && (
        <div className="cq-room-code">
          <div>
            <p>¿Quieres jugarlo? Inscríbete con tu cuenta de ChessQuery.</p>
            <Link to={`/app/torneos/${d.tournament.id}`}>Inscribirme en este torneo</Link>
          </div>
          <QrCode value={`${window.location.origin}/app/torneos/${d.tournament.id}`} label="QR para inscribirse en el torneo" size={120} />
        </div>
      )} />
    </main>
  );
};
