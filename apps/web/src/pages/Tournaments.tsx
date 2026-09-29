import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { publicTournamentsApi, tournamentsApi } from '../api/tournaments';
import type { TournamentDetail, TournamentView } from '../api/tournamentTypes';
import { StatusMessage } from '../components/StatusMessage';
import { TournamentDetailView, tournamentKeys } from '../components/tournament/TournamentDetailView';
import { FederationCalendar, TournamentList } from './PublicTournaments';

const useRefreshTournaments = (id?: number) => {
  const qc = useQueryClient();
  return () => {
    ['my-tournaments', 'open-tournaments'].forEach((k) => void qc.invalidateQueries({ queryKey: [k] }));
    if (id) tournamentKeys(id).forEach((queryKey) => void qc.invalidateQueries({ queryKey }));
  };
};

/** Inscribirme / retirarme de un torneo con inscripciones abiertas. */
const JoinButton = ({ t, registered }: { t: TournamentView; registered: boolean }) => {
  const refresh = useRefreshTournaments(t.id);
  const toggle = useMutation({ mutationFn: () => (registered ? tournamentsApi.leave(t.id) : tournamentsApi.join(t.id)), onSuccess: refresh });
  if (t.status !== 'OPEN') return null;
  return (
    <div className="cq-actions">
      <Button size="sm" variant={registered ? 'secondary' : 'primary'} loading={toggle.isPending} onClick={() => toggle.mutate()}>
        {registered ? 'Retirarme' : 'Inscribirme'}
      </Button>
      <StatusMessage error={toggle.error} />
    </div>
  );
};

/** /app/torneos: mis torneos, torneos abiertos para inscribirme y el calendario federativo. */
export const Tournaments = () => {
  const mine = useQuery({ queryKey: ['my-tournaments'], queryFn: tournamentsApi.mine });
  const open = useQuery({ queryKey: ['open-tournaments'], queryFn: () => publicTournamentsApi.list('OPEN') });
  const registeredIds = new Set((mine.data?.registered ?? []).map((t) => t.id));
  return (
    <div className="cq-page">
      <h1>Torneos</h1>
      <Card header="Mis torneos">
        <TournamentList items={mine.data?.registered} base="/app/torneos" empty="Aún no te inscribes en un torneo" />
      </Card>
      <Card header="Inscripciones abiertas">
        <TournamentList items={open.data} base="/app/torneos" empty="No hay torneos con inscripciones abiertas"
                        action={(t) => <JoinButton t={t} registered={registeredIds.has(t.id)} />} />
      </Card>
      <FederationCalendar />
    </div>
  );
};

/** Mi mesa en la ronda en curso, destacada arriba del detalle. */
const MyBoard = ({ d, playerId }: { d: TournamentDetail; playerId: number }) => {
  const registered = d.players.some((p) => p.player.playerId === playerId);
  if (!registered) return <JoinButton t={d.tournament} registered={false} />;
  return (
    <>
      <p className="cq-ok" role="status">Estás inscrito en este torneo.</p>
      <JoinButton t={d.tournament} registered />
    </>
  );
};

/** /app/torneos/:id: detalle con inscripción. */
export const TournamentPage = () => {
  const { id } = useParams();
  const me = useMe();
  const playerId = me.data?.profile.id ?? 0;
  return <TournamentDetailView id={Number(id)} actions={(d) => <MyBoard d={d} playerId={playerId} />} />;
};
