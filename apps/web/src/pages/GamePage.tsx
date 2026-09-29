import { useParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { gamesApi, pgnUrl } from '../api/games';
import type { GameSide, GameView } from '../api/gameTypes';
import { StatusMessage } from '../components/StatusMessage';
import { ChessClock } from '../components/game/ChessClock';
import { PlayBoard } from '../components/game/PlayBoard';
import { useLiveGame } from '../components/game/useLiveGame';
import { ratingDelta, resultFor, timeControl } from '../components/game/labels';

/** Acción sobre la partida: la respuesta del servidor reemplaza la caché (sin esperar al long poll). */
const useGameAction = (id: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (run: (id: number) => Promise<GameView>) => run(id),
    onSuccess: (g) => { qc.setQueryData(['game', id], g); void qc.invalidateQueries({ queryKey: ['me'] }); },
  });
};

const PlayerBar = ({ side, running, active }: { side: GameSide; running: boolean; active: boolean }) => (
  <div className="cq-player-bar">
    <span><strong>{side.name}</strong> <span className="cq-muted">{side.ratingBefore ?? ''}</span></span>
    {active || side.clockMs > 0 ? <ChessClock ms={side.clockMs} running={running} label={side.name} /> : null}
  </div>
);

/** Movimientos en pares numerados: 1. e4 e5 2. Ac4 … */
const MoveList = ({ san }: { san: string[] }) => (
  <ol className="cq-moves" aria-label="Jugadas">
    {Array.from({ length: Math.ceil(san.length / 2) }, (_, i) => (
      <li key={i}>{san[2 * i]} {san[2 * i + 1] ?? ''}</li>
    ))}
  </ol>
);

/** Qué puede hacer el jugador según el estado: aceptar/rechazar, abandonar, tablas. */
const GameActions = ({ g, myId }: { g: GameView; myId: number }) => {
  const action = useGameAction(g.id);
  const rivalOffered = g.drawOfferBy != null && g.drawOfferBy !== myId;
  if (g.status === 'PENDING') {
    const challenged = g.challengerId !== myId;
    return (
      <div className="cq-actions">
        {challenged ? (
          <>
            <Button onClick={() => action.mutate(gamesApi.accept)} loading={action.isPending}>Aceptar desafío</Button>
            <Button variant="secondary" onClick={() => action.mutate(gamesApi.decline)}>Rechazar</Button>
          </>
        ) : (
          <>
            <span className="cq-muted" role="status">Esperando que tu rival acepte…</span>
            <Button variant="secondary" onClick={() => action.mutate(gamesApi.cancel)}>Cancelar desafío</Button>
          </>
        )}
        <StatusMessage error={action.error} />
      </div>
    );
  }
  if (g.status !== 'ACTIVE') return null;
  return (
    <div className="cq-actions">
      {rivalOffered ? (
        <>
          <span role="status">Tu rival ofrece tablas.</span>
          <Button size="sm" onClick={() => action.mutate(gamesApi.acceptDraw)}>Aceptar tablas</Button>
          <Button size="sm" variant="secondary" onClick={() => action.mutate(gamesApi.declineDraw)}>Rechazar</Button>
        </>
      ) : (
        <Button size="sm" variant="secondary" disabled={g.drawOfferBy === myId} onClick={() => action.mutate(gamesApi.offerDraw)}>
          {g.drawOfferBy === myId ? 'Tablas ofrecidas' : 'Ofrecer tablas'}
        </Button>
      )}
      <Button size="sm" variant="secondary" onClick={() => { if (window.confirm('¿Abandonar la partida?')) action.mutate(gamesApi.resign); }}>
        Abandonar
      </Button>
      <StatusMessage error={action.error} />
    </div>
  );
};

/** Resultado final con cambio de rating de cada lado y descarga del PGN. */
const ResultCard = ({ g, myId }: { g: GameView; myId: number }) => {
  if (g.status === 'FINISHED') {
    const line = (s: GameSide) => `${s.name}: ${s.ratingAfter ?? s.ratingBefore ?? '—'} (${ratingDelta(s.ratingBefore, s.ratingAfter) ?? 'sin cambio'})`;
    return (
      <Card header={`${resultFor(g.result, myId, g)} · ${g.terminationLabel}`}>
        {g.rated && <p style={{ margin: 0 }}>{line(g.white)} · {line(g.black)}</p>}
        <p><a href={pgnUrl(g.id)} download>Descargar PGN</a></p>
      </Card>
    );
  }
  const text: Partial<Record<GameView['status'], string>> = {
    DECLINED: 'El desafío fue rechazado.', CANCELLED: 'El desafío fue cancelado.', EXPIRED: 'El desafío venció sin respuesta.',
  };
  return text[g.status] ? <p role="status" className="cq-muted">{text[g.status]}</p> : null;
};

/** Tablero con las barras de cada jugador; abajo siempre quien mira (o blancas si es espectador). */
const BoardArea = ({ g, myColor }: { g: GameView; myColor: 'WHITE' | 'BLACK' | null }) => {
  const move = useGameAction(g.id);
  const active = g.status === 'ACTIVE';
  const [top, bottom] = myColor === 'BLACK' ? [g.white, g.black] : [g.black, g.white];
  const running = (s: GameSide) => active && (g.sideToMove === 'WHITE') === (s.playerId === g.white.playerId);
  const myTurn = active && g.sideToMove === myColor;
  return (
    <div>
      <PlayerBar side={top} running={running(top)} active={active} />
      <PlayBoard fen={g.fen} myColor={myColor} canMove={myTurn} lastMove={g.moves[g.moves.length - 1]}
                 onMove={(uci) => move.mutate((gid) => gamesApi.move(gid, uci))} />
      <PlayerBar side={bottom} running={running(bottom)} active={active} />
      <StatusMessage error={move.error} />
      {active && myColor && <p className="cq-muted" role="status">{myTurn ? 'Tu turno' : 'Turno de tu rival'}</p>}
    </div>
  );
};

const colorOf = (g: GameView, myId: number) => {
  if (g.white.playerId === myId) return 'WHITE';
  return g.black.playerId === myId ? 'BLACK' : null;
};

/** /app/partidas/:id: tablero, relojes, jugadas y acciones; se actualiza en vivo por long polling. */
export const GamePage = () => {
  const id = Number(useParams().id);
  const me = useMe();
  const game = useLiveGame(id);
  if (game.isLoading || !me.data) return <Skeleton height={320} />;
  if (game.error || !game.data) return <ErrorAlert message="No encontramos esa partida" onRetry={() => void game.refetch()} />;
  const g = game.data;
  const myId = me.data.profile.id;
  const myColor = colorOf(g, myId);
  return (
    <div className="cq-page">
      <div className="cq-actions">
        <h1 style={{ margin: 0 }}>{g.white.name} vs {g.black.name}</h1>
        <Badge>{timeControl(g)}</Badge>
        {g.rated && <Badge variant="gold">Por rating</Badge>}
      </div>
      <div className="cq-game">
        <BoardArea g={g} myColor={myColor} />
        <div className="cq-page">
          <ResultCard g={g} myId={myId} />
          {myColor && <GameActions g={g} myId={myId} />}
          <Card header="Jugadas"><MoveList san={g.san} /></Card>
        </div>
      </div>
    </div>
  );
};
