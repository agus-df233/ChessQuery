import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { publicTournamentsApi, tournamentsApi } from '../api/tournaments';
import type { RoundView, TournamentDetail, TournamentView } from '../api/tournamentTypes';
import { StatusMessage } from '../components/StatusMessage';
import { TournamentDetailView, tournamentKeys, useTournament } from '../components/tournament/TournamentDetailView';
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
  const { rounds } = useTournament(d.tournament.id);
  const registered = d.players.some((p) => p.player.playerId === playerId);
  if (!registered) return <JoinButton t={d.tournament} registered={false} />;
  const current = d.tournament.status === 'IN_PROGRESS' ? rounds.data?.[rounds.data.length - 1] : undefined;
  return (
    <>
      <p className="cq-ok" role="status">{current ? myPairing(current, playerId) : 'Estás inscrito en este torneo.'}</p>
      <JoinButton t={d.tournament} registered />
    </>
  );
};

/** "Ronda 2 · mesa 3: juegas con blancas contra Luis Paz" (o "descansas", si te tocó bye). */
export const myPairing = (round: RoundView, playerId: number) => {
  const board = round.boards.find((b) => b.white.playerId === playerId || b.black?.playerId === playerId);
  const prefix = `Ronda ${round.number}`;
  if (!board) return `${prefix}: no tienes mesa en esta ronda.`;
  if (!board.black) return `${prefix}: descansas (bye, suma 1 punto).`;
  const white = board.white.playerId === playerId;
  const rival = white ? board.black : board.white;
  return `${prefix} · mesa ${board.board}: juegas con ${white ? 'blancas' : 'negras'} contra ${rival.name}.`;
};

/** /app/torneos/:id: detalle con inscripción. */
export const TournamentPage = () => {
  const { id } = useParams();
  const me = useMe();
  const playerId = me.data?.profile.id ?? 0;
  return <TournamentDetailView id={Number(id)} actions={(d) => <MyBoard d={d} playerId={playerId} />} />;
};
