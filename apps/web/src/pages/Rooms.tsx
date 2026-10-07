import { FormEvent, useEffect, useRef, useState } from 'react';
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { roomsApi } from '../api/rooms';
import type { RoomView } from '../api/roomTypes';
import { useMe } from '../api/hooks';
import { StatusMessage } from '../components/StatusMessage';
import { BoardGrid } from '../components/room/BoardGrid';
import { roomKey, useLiveRoom } from '../components/room/useLiveRoom';
import { CATEGORY_LABEL } from '../lib/timeControl';
import { RoomList } from './OrganizerRooms';

/** /app/salas: entrar a una sala con su código (o con el enlace del QR, que lo trae en `?codigo=`). */
export const Rooms = () => {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const [code, setCode] = useState(params.get('codigo') ?? '');
  const mine = useQuery({ queryKey: ['my-rooms'], queryFn: roomsApi.mine });
  const join = useMutation({
    mutationFn: roomsApi.join,
    onSuccess: (r) => { qc.setQueryData(roomKey(r.id), r); void qc.invalidateQueries({ queryKey: ['my-rooms'] }); navigate(`/app/salas/${r.id}`); },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); join.mutate(code); };
  return (
    <div className="cq-page">
      <h1>Salas de juego</h1>
      <Card header="Entrar a una sala">
        <form onSubmit={submit} className="cq-actions">
          <label>Código de la sala
            <input required maxLength={20} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())}
                   autoComplete="off" placeholder="AB3K9Q" style={{ letterSpacing: '0.2em', textTransform: 'uppercase' }} />
          </label>
          <Button type="submit" loading={join.isPending}>Entrar</Button>
          <StatusMessage error={join.error} />
        </form>
      </Card>
      <Card header="Salas en las que estuve">
        <RoomList items={mine.data?.joined} base="/app/salas" empty="Todavía no entras a ninguna sala" />
      </Card>
    </div>
  );
};

const myBoardOf = (r?: RoomView) => r?.boards.find((b) => b.boardNo === r.myBoard);
const playing = (r?: RoomView) => myBoardOf(r)?.game?.status === 'ACTIVE';

/** Cuando empieza su partida, a jugar (una sola vez por partida: si vuelve a la sala, no lo saca de nuevo). */
const useGoToMyGame = (r?: RoomView) => {
  const navigate = useNavigate();
  const announced = useRef<number | null>(null);
  const gameId = r?.myGameId;
  const active = playing(r);
  useEffect(() => {
    if (gameId && active && announced.current !== gameId) {
      announced.current = gameId;
      navigate(`/app/partidas/${gameId}`);
    }
  }, [gameId, active, navigate]);
};

/** Lo que le toca al jugador en la sala, en una frase. */
const MySeat = ({ room, myId }: { room: RoomView; myId: number }) => {
  const board = myBoardOf(room);
  if (!board) return <p>Estás mirando como espectador. El organizador te asignará un tablero.</p>;
  if (playing(room)) {
    return <p>Estás jugando en el tablero {board.boardNo}. <Link to={`/app/partidas/${room.myGameId}`}>Ir a mi partida</Link></p>;
  }
  const color = board.white?.playerId === myId ? 'blancas' : 'negras';
  return <p>Te toca el tablero {board.boardNo} con {color}: la partida empieza cuando el organizador la inicie.</p>;
};

/**
 * /app/salas/:id: la antesala del jugador. Si el organizador le asigna un tablero y la partida empieza, lo lleva a su
 * partida; mientras tanto (o si no tiene tablero) mira la cuadrícula como espectador.
 */
export const RoomPage = () => {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const me = useMe();
  const room = useLiveRoom(id);
  const leave = useMutation({
    mutationFn: () => roomsApi.leave(id, me.data!.profile.id),
    onSuccess: () => navigate('/app/salas'),
  });
  const r = room.data;
  useGoToMyGame(r);

  if (room.isLoading || !me.data) return <Skeleton height={320} />;
  if (room.error || !r) return <ErrorAlert message="No estás en esa sala: entra con su código" onRetry={() => navigate('/app/salas')} />;
  if (r.organizer) return <Navigate to={`/club/salas/${id}`} replace />;
  return (
    <div className="cq-page">
      <p><Link to="/app/salas">← Salas</Link></p>
      <div className="cq-actions">
        <h1 style={{ margin: 0 }}>{r.name}</h1>
        <Badge>{CATEGORY_LABEL[r.category]} {r.initialSeconds / 60}+{r.incrementSeconds}</Badge>
        {r.status === 'CLOSED' && <Badge>Cerrada</Badge>}
      </div>
      <Card>
        <div role="status"><MySeat room={r} myId={me.data.profile.id} /></div>
        {!playing(r) && (
          <div className="cq-actions">
            <Button size="sm" variant="secondary" loading={leave.isPending} onClick={() => leave.mutate()}>Salir de la sala</Button>
            <StatusMessage error={leave.error} />
          </div>
        )}
      </Card>
      <BoardGrid boards={r.boards} />
    </div>
  );
};
