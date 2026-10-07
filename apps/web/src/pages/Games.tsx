import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, EmptyState, Skeleton } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { gamesApi } from '../api/games';
import type { GameView } from '../api/gameTypes';
import { friendsApi } from '../api/users';
import { StatusMessage } from '../components/StatusMessage';
import { ChallengeButton } from '../components/game/ChallengeButton';
import { OpenChallengeCard } from '../components/game/OpenChallenge';
import { opponentOf, ratingDelta, resultFor, timeControl } from '../components/game/labels';

const GameRow = ({ g, me, children }: { g: GameView; me: number; children?: React.ReactNode }) => {
  const rival = opponentOf(g, me);
  const mySide = g.white.playerId === me ? g.white : g.black;
  const delta = ratingDelta(mySide.ratingBefore, mySide.ratingAfter);
  return (
    <li className="cq-tournament-item">
      <div>
        <Link to={`/app/partidas/${g.id}`} style={{ fontWeight: 600 }}>vs {rival.name}</Link>
        <div className="cq-muted">
          {[timeControl(g), g.white.playerId === me ? 'blancas' : 'negras', resultFor(g.result, me, g), g.terminationLabel, delta]
            .filter(Boolean).join(' · ')}
        </div>
      </div>
      {children}
    </li>
  );
};

const Section = ({ title, games, me, empty, action }: {
  title: string; games: GameView[]; me: number; empty?: string; action?: (g: GameView) => React.ReactNode;
}) => {
  if (!games.length && !empty) return null;
  return (
    <Card header={title}>
      {games.length ? <ul className="cq-list">{games.map((g) => <GameRow key={g.id} g={g} me={me}>{action?.(g)}</GameRow>)}</ul>
        : <p className="cq-muted">{empty}</p>}
    </Card>
  );
};

/** Desafiar a un amigo desde "Mis partidas". */
const ChallengeFriends = () => {
  const friends = useQuery({ queryKey: ['friends'], queryFn: friendsApi.list });
  if (!friends.data) return null;
  if (!friends.data.length) {
    return <Card header="Nueva partida"><EmptyState title="Agrega amigos para desafiarlos"
      action={<Link to="/app/jugadores"><Button size="sm">Buscar jugadores</Button></Link>} /></Card>;
  }
  return (
    <Card header="Nueva partida">
      <ul className="cq-list">
        {friends.data.map((f) => (
          <li key={f.playerId} className="cq-tournament-item">
            <span>{f.firstName} {f.lastName}</span>
            <ChallengeButton opponentId={f.playerId} opponentName={`${f.firstName} ${f.lastName}`} />
          </li>
        ))}
      </ul>
    </Card>
  );
};

/** /app/partidas: desafíos recibidos y enviados, partidas en curso, historial y desafiar a un amigo. */
export const Games = () => {
  const me = useMe();
  const qc = useQueryClient();
  const mine = useQuery({ queryKey: ['my-games'], queryFn: gamesApi.mine, refetchInterval: 10_000 });
  const respond = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'accept' | 'decline' | 'cancel' }) => gamesApi[action](id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['my-games'] }),
  });
  const myId = me.data?.profile.id;
  if (!mine.data || myId === undefined) return <Skeleton height={200} />;
  const m = mine.data;
  return (
    <div className="cq-page">
      <h1>Mis partidas</h1>
      <StatusMessage error={respond.error} />
      <Section title="Te desafiaron" games={m.incoming} me={myId} action={(g) => (
        <div className="cq-actions">
          <Button size="sm" onClick={() => respond.mutate({ id: g.id, action: 'accept' })}>Aceptar</Button>
          <Button size="sm" variant="secondary" onClick={() => respond.mutate({ id: g.id, action: 'decline' })}>Rechazar</Button>
        </div>
      )} />
      <Section title="En juego" games={m.active} me={myId} action={(g) => <Link to={`/app/partidas/${g.id}`}><Button size="sm">Ir a la partida</Button></Link>} />
      <Section title="Desafíos enviados" games={m.outgoing} me={myId} action={(g) => (
        <Button size="sm" variant="secondary" onClick={() => respond.mutate({ id: g.id, action: 'cancel' })}>Cancelar</Button>
      )} />
      <ChallengeFriends />
      <OpenChallengeCard />
      <Section title="Historial" games={m.finished} me={myId} empty="Aún no terminas partidas." />
    </div>
  );
};
