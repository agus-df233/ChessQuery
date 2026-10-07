import { useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { publicTournamentsApi, tournamentsApi } from '../api/tournaments';
import type { RegistrationView, RoundView, TournamentDetail, TournamentView } from '../api/tournamentTypes';
import { QrCode } from '../components/QrCode';
import { StatusMessage } from '../components/StatusMessage';
import { TournamentDetailView, tournamentKeys, useTournament } from '../components/tournament/TournamentDetailView';
import { FederationCalendar, TournamentList } from './PublicTournaments';

const useRefreshTournaments = (id?: number) => {
  const qc = useQueryClient();
  return () => {
    ['my-tournaments', 'open-tournaments'].forEach((k) => void qc.invalidateQueries({ queryKey: [k] }));
    if (id) void qc.invalidateQueries({ queryKey: ['my-registration', id] });
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

/** Estado de mi inscripción en una frase (la API decide: pendiente, espera, confirmada o retirada). */
export const registrationMessage = (r: RegistrationView) => {
  if (r.status === 'PENDING') return 'Tu inscripción espera la aprobación del organizador.';
  if (r.status === 'WAITLIST') return 'Estás en lista de espera: entras solo si se libera un cupo.';
  if (r.status === 'WITHDRAWN') return r.withdrawnFromRound === 1 ? 'No te acreditaste: no juegas este torneo.' : `Te retiraste desde la ronda ${r.withdrawnFromRound}.`;
  return 'Tu inscripción está confirmada.';
};

/** Mi inscripción: estado, mi QR para acreditarme el día del torneo y mi mesa en la ronda en curso. */
const MyBoard = ({ d, playerId }: { d: TournamentDetail; playerId: number }) => {
  const { rounds } = useTournament(d.tournament.id);
  const mine = useQuery({ queryKey: ['my-registration', d.tournament.id], queryFn: () => tournamentsApi.myRegistration(d.tournament.id), retry: false });
  if (mine.isLoading) return null;
  if (!mine.data) return <JoinButton t={d.tournament} registered={false} />;
  const r = mine.data;
  const current = d.tournament.status === 'IN_PROGRESS' && r.status === 'CONFIRMED' ? rounds.data?.[rounds.data.length - 1] : undefined;
  const showQr = d.tournament.status === 'OPEN' && d.tournament.checkinRequired && r.status === 'CONFIRMED';
  return (
    <Card>
      <p className="cq-ok" role="status">{current ? myPairing(current, playerId) : registrationMessage(r)}</p>
      {showQr && (
        <div className="cq-room-code">
          <p>{r.checkedInAt ? 'Ya estás acreditado.' : 'El día del torneo muestra este QR al organizador para acreditarte.'}</p>
          <QrCode value={r.checkinCode} label="Mi QR de acreditación" />
        </div>
      )}
      <JoinButton t={d.tournament} registered />
    </Card>
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
