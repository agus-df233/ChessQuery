import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { openChallengesApi } from '../api/games';
import type { OpenChallengeView } from '../api/gameTypes';
import { StatusMessage } from '../components/StatusMessage';
import { WaitingForRival, useWaitForRival } from '../components/game/OpenChallenge';
import { timeControlLabel } from '../lib/timeControl';

const COLOR_FOR_ME = { WHITE: 'con negras', BLACK: 'con blancas', RANDOM: 'con color al azar' } as const;

/** Mensajes de un desafío que ya no se puede aceptar. */
const CLOSED: Record<string, (c: OpenChallengeView) => string> = {
  EXPIRED: () => 'Este desafío venció sin que nadie lo aceptara.',
  CANCELLED: (c) => `${c.challengerName} canceló este desafío.`,
  ACCEPTED: () => 'Otra persona aceptó este desafío antes.',
};

/** La invitación para quien abre el enlace de otro. */
const Invitation = ({ c, onAccept, pending }: { c: OpenChallengeView; onAccept: () => void; pending: boolean }) => (
  <>
    <p>
      {c.challengerName} te desafía a una partida {timeControlLabel(c.minutes, c.incrementSeconds).toLowerCase()}, jugarías{' '}
      {COLOR_FOR_ME[c.color]}{c.rated ? ' y cuenta para tu ELO de ese ritmo' : ' (amistosa, sin ELO)'}.
    </p>
    <Button loading={pending} onClick={onAccept}>Aceptar y jugar</Button>
  </>
);

/** Aceptar (lleva a la partida) o cancelar el propio desafío (vuelve a Mis partidas). */
const useChallengeActions = (token: string) => {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const accept = useMutation({ mutationFn: () => openChallengesApi.accept(token), onSuccess: (g) => navigate(`/app/partidas/${g.id}`) });
  const cancel = useMutation({
    mutationFn: () => openChallengesApi.cancel(token),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['open-challenges'] }); navigate('/app/partidas'); },
  });
  return { accept, cancel, error: accept.error ?? cancel.error };
};

const ChallengeBody = ({ c, actions }: { c: OpenChallengeView; actions: ReturnType<typeof useChallengeActions> }) => {
  if (c.status !== 'OPEN') return <p role="status">{CLOSED[c.status](c)}</p>;
  if (c.mine) return <WaitingForRival challenge={c} onCancel={() => actions.cancel.mutate()} />;
  return <Invitation c={c} onAccept={() => actions.accept.mutate()} pending={actions.accept.isPending} />;
};

/** /app/desafio/:token: lo que abre el enlace o el QR de un desafío abierto. Aceptar lleva directo a la partida. */
export const OpenChallengePage = () => {
  const token = useParams().token ?? '';
  const navigate = useNavigate();
  const view = useWaitForRival(token);
  const actions = useChallengeActions(token);
  if (view.isLoading) return <Skeleton height={200} />;
  if (!view.data) return <ErrorAlert message="Ese desafío no existe" onRetry={() => navigate('/app/partidas')} />;
  return (
    <div className="cq-page">
      <p><Link to="/app/partidas">← Mis partidas</Link></p>
      <h1>Desafío de {view.data.challengerName}</h1>
      <Card>
        <ChallengeBody c={view.data} actions={actions} />
        <StatusMessage error={actions.error} />
      </Card>
    </div>
  );
};
