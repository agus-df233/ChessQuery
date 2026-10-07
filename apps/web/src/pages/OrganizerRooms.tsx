import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { roomsApi } from '../api/rooms';
import type { RoomBoard, RoomSummary, RoomView } from '../api/roomTypes';
import { StatusMessage } from '../components/StatusMessage';
import { BoardGrid } from '../components/room/BoardGrid';
import { EMPTY_ROOM, RoomForm } from '../components/room/RoomForm';
import { RoomQr } from '../components/room/RoomQr';
import { roomKey, useLiveRoom } from '../components/room/useLiveRoom';
import { CATEGORY_LABEL } from '../lib/timeControl';

export const RoomList = ({ items, base, empty }: { items?: RoomSummary[]; base: string; empty: string }) => {
  if (!items) return <Skeleton height={80} />;
  if (items.length === 0) return <p className="cq-muted">{empty}</p>;
  return (
    <ul className="cq-list">
      {items.map((r) => (
        <li key={r.id}>
          <Link to={`${base}/${r.id}`}>{r.name}</Link>{' '}
          <span className="cq-muted">
            · {r.boardCount} tableros · {r.memberCount}/{r.maxPlayers} jugadores · {CATEGORY_LABEL[r.category]}
            {r.code && r.status === 'OPEN' ? ` · código ${r.code}` : ''}
          </span>{' '}
          {r.status === 'CLOSED' && <Badge>Cerrada</Badge>}
        </li>
      ))}
    </ul>
  );
};

/** /club/salas: salas de juego del club (clases, prácticas) y creación de una nueva. */
export const OrganizerRooms = () => {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const mine = useQuery({ queryKey: ['my-rooms'], queryFn: roomsApi.mine });
  const create = useMutation({
    mutationFn: roomsApi.create,
    onSuccess: (r) => { void qc.invalidateQueries({ queryKey: ['my-rooms'] }); navigate(`/club/salas/${r.id}`); },
  });
  return (
    <div className="cq-page">
      <p><Link to="/club">← Mi club</Link></p>
      <h1>Salas de juego</h1>
      <p className="cq-muted">
        Para una clase o una práctica: despliegas tableros, tus alumnos entran con un código y tú los asignas. Ves todos los
        tableros en vivo, de a 4 por pantalla.
      </p>
      <Card header="Nueva sala">
        <RoomForm initial={EMPTY_ROOM} submitLabel="Abrir sala" pending={create.isPending} error={create.error}
                  onSubmit={(req) => create.mutate(req)} />
      </Card>
      <Card header="Mis salas">
        <RoomList items={mine.data?.organized} base="/club/salas" empty="Todavía no abres salas" />
      </Card>
    </div>
  );
};

const inPlay = (b: RoomBoard) => b.game?.status === 'ACTIVE';

/** Puestos de un tablero: elegir quién juega con blancas y con negras entre los miembros de la sala. */
const SeatPicker = ({ room, board, onAssign }: { room: RoomView; board: RoomBoard; onAssign: (w: number | null, b: number | null) => void }) => {
  const [white, setWhite] = useState<number | null>(board.white?.playerId ?? null);
  const [black, setBlack] = useState<number | null>(board.black?.playerId ?? null);
  useEffect(() => { setWhite(board.white?.playerId ?? null); setBlack(board.black?.playerId ?? null); }, [board.white, board.black]);
  const select = (label: string, value: number | null, set: (v: number | null) => void) => (
    <label>{label}
      <select value={value ?? ''} onChange={(e) => set(e.target.value ? Number(e.target.value) : null)}>
        <option value="">— libre —</option>
        {room.members.map((m) => (
          <option key={m.playerId} value={m.playerId}>{m.name}{m.boardNo && m.boardNo !== board.boardNo ? ` (tablero ${m.boardNo})` : ''}</option>
        ))}
      </select>
    </label>
  );
  return (
    <>
      {select(`Blancas tablero ${board.boardNo}`, white, setWhite)}
      {select(`Negras tablero ${board.boardNo}`, black, setBlack)}
      <Button size="sm" variant="secondary" onClick={() => onAssign(white, black)}>Guardar puestos</Button>
    </>
  );
};

/** /club/salas/:id: código y QR, miembros, puestos de cada tablero y la cuadrícula en vivo (modo proyector). */
export const OrganizerRoom = () => {
  const id = Number(useParams().id);
  const qc = useQueryClient();
  const room = useLiveRoom(id);
  const [projector, setProjector] = useState(false);
  const [editing, setEditing] = useState(false);
  const act = useMutation({
    mutationFn: (fn: () => Promise<RoomView>) => fn(),
    onSuccess: (r) => { qc.setQueryData(roomKey(id), r); void qc.invalidateQueries({ queryKey: ['my-rooms'] }); setEditing(false); },
  });
  useEffect(() => {
    if (!projector) return undefined;
    const exit = (e: KeyboardEvent) => { if (e.key === 'Escape') setProjector(false); };
    window.addEventListener('keydown', exit);
    return () => window.removeEventListener('keydown', exit);
  }, [projector]);

  if (room.isLoading) return <Skeleton height={320} />;
  if (room.error || !room.data) return <ErrorAlert message="No encontramos esa sala" onRetry={() => void room.refetch()} />;
  const r = room.data;
  const open = r.status === 'OPEN';
  const boardActions = (b: RoomBoard) => {
    if (!open) return null;
    if (inPlay(b)) return <span className="cq-muted">Partida en curso</span>;
    return (
      <>
        <SeatPicker room={r} board={b} onAssign={(w, bl) => act.mutate(() => roomsApi.assign(id, b.boardNo, { whitePlayerId: w, blackPlayerId: bl }))} />
        {b.white && b.black && !b.game && <Button size="sm" onClick={() => act.mutate(() => roomsApi.start(id, b.boardNo))}>Iniciar</Button>}
        {b.white && b.black && b.game && <Button size="sm" onClick={() => act.mutate(() => roomsApi.rematch(id, b.boardNo))}>Revancha</Button>}
      </>
    );
  };

  if (projector) {
    return (
      <div className="cq-room-fullscreen" role="dialog" aria-modal="true" aria-label={`${r.name}: tableros en pantalla completa`}>
        <div className="cq-actions" style={{ justifyContent: 'space-between' }}>
          <h1 style={{ margin: 0 }}>{r.name}</h1>
          <Button size="sm" variant="secondary" onClick={() => setProjector(false)}>Salir de pantalla completa</Button>
        </div>
        <BoardGrid boards={r.boards} />
      </div>
    );
  }

  return (
    <div className="cq-page">
      <p><Link to="/club/salas">← Salas de juego</Link></p>
      <div className="cq-actions">
        <h1 style={{ margin: 0 }}>{r.name}</h1>
        <Badge>{CATEGORY_LABEL[r.category]} {r.initialSeconds / 60}+{r.incrementSeconds}</Badge>
        <Badge>Sin ELO</Badge>
        {!open && <Badge>Cerrada</Badge>}
      </div>
      {open && <Card header="Para entrar"><RoomQr code={r.code} /></Card>}
      <div className="cq-actions">
        {open && <Button onClick={() => act.mutate(() => roomsApi.startAll(id))}>Iniciar todos los tableros listos</Button>}
        <Button variant="secondary" onClick={() => setProjector(true)}>Pantalla completa (proyector)</Button>
        {open && <Button variant="secondary" onClick={() => setEditing(!editing)}>{editing ? 'Cancelar cambios' : 'Cambiar tableros o cupo'}</Button>}
        {open && <Button variant="danger" onClick={() => { if (window.confirm('¿Cerrar la sala? Nadie más podrá entrar ni empezar partidas.')) act.mutate(() => roomsApi.close(id)); }}>Cerrar sala</Button>}
        <StatusMessage error={act.error} />
      </div>
      {editing && (
        <Card header="Configuración">
          <RoomForm initial={{ name: r.name, boards: r.boardCount, maxPlayers: r.maxPlayers, minutes: r.initialSeconds / 60, incrementSeconds: r.incrementSeconds }}
                    submitLabel="Guardar" pending={act.isPending} error={null}
                    onSubmit={(req) => act.mutate(() => roomsApi.update(id, req))} />
        </Card>
      )}
      <Card header={`Jugadores (${r.members.length}/${r.maxPlayers})`}>
        {r.members.length === 0 ? <p className="cq-muted">Todavía no entra nadie: comparte el código o el QR.</p> : (
          <ul className="cq-list">
            {r.members.map((m) => {
              const playing = r.boards.some((b) => b.boardNo === m.boardNo && inPlay(b));
              return (
                <li key={m.playerId}>
                  {m.name} <span className="cq-muted">· {m.boardNo ? `tablero ${m.boardNo}, ${m.color === 'WHITE' ? 'blancas' : 'negras'}` : 'espectador'}</span>{' '}
                  {open && !playing && (
                    <Button size="sm" variant="secondary" onClick={() => act.mutate(() => roomsApi.remove(id, m.playerId))}>Sacar de la sala</Button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </Card>
      <BoardGrid boards={r.boards} actions={boardActions} />
    </div>
  );
};
