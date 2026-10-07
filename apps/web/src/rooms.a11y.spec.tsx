import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as axeMatchers from 'vitest-axe/matchers';
import type { GameView } from './api/gameTypes';
import type { RoomBoard, RoomView } from './api/roomTypes';

expect.extend(axeMatchers);

/** Salas de juego: cuadrícula de 4 por pantalla, panel del organizador, entrar con código y antesala, con axe. */
const fx = vi.hoisted(() => {
  const game = (id: number, status: 'ACTIVE' | 'FINISHED'): GameView => ({
    id, status, challengerId: 100, initialSeconds: 600, incrementSeconds: 5, category: 'RAPID', rated: false,
    white: { playerId: 1, name: 'Alumno1 A.', ratingBefore: 1200, ratingAfter: null, clockMs: 590_000 },
    black: { playerId: 2, name: 'Alumno2 B.', ratingBefore: 1200, ratingAfter: null, clockMs: 600_000 },
    fen: 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1', moves: ['e2e4'], san: ['e4'], ply: 1,
    sideToMove: 'BLACK', drawOfferBy: null, result: status === 'FINISHED' ? 'WHITE_WINS' : null,
    termination: null, terminationLabel: status === 'FINISHED' ? 'abandono' : null, version: 3,
    createdAt: '2026-10-07T12:00:00Z', finishedAt: null, roomId: 9, boardNo: 1,
  });
  const boards = (n: number): RoomBoard[] => Array.from({ length: n }, (_, i) => ({ boardNo: i + 1, white: null, black: null, game: null }));
  const room: RoomView = {
    id: 9, name: 'Clase 4°B', code: 'AB3K9Q', status: 'OPEN', boardCount: 6, maxPlayers: 14, initialSeconds: 600,
    incrementSeconds: 5, category: 'RAPID', version: 4, organizer: true, myBoard: null, myGameId: null,
    members: [
      { playerId: 1, name: 'Alumno1 A.', boardNo: 1, color: 'WHITE' },
      { playerId: 2, name: 'Alumno2 B.', boardNo: 1, color: 'BLACK' },
      { playerId: 3, name: 'Alumno3 C.', boardNo: null, color: null },
      { playerId: 4, name: 'Alumno4 D.', boardNo: null, color: null },
    ],
    boards: [
      { boardNo: 1, white: { playerId: 1, name: 'Alumno1 A.' }, black: { playerId: 2, name: 'Alumno2 B.' }, game: game(50, 'ACTIVE') },
      ...boards(6).slice(1),
    ],
  };
  return { game, room };
});

vi.mock('./api/rooms', () => ({
  roomsApi: {
    get: vi.fn().mockResolvedValue(fx.room),
    waitChange: vi.fn(() => new Promise(() => undefined)),
    mine: vi.fn().mockResolvedValue({ organized: [], joined: [{ id: 9, name: 'Clase 4°B', code: null, status: 'OPEN', boardCount: 6, maxPlayers: 14, memberCount: 4, category: 'RAPID', createdAt: '' }] }),
    create: vi.fn(), update: vi.fn(), close: vi.fn(), remove: vi.fn(), rematch: vi.fn(),
    join: vi.fn().mockResolvedValue(fx.room),
    leave: vi.fn().mockResolvedValue(undefined),
    assign: vi.fn().mockResolvedValue(fx.room),
    start: vi.fn().mockResolvedValue(fx.room),
    startAll: vi.fn().mockResolvedValue(fx.room),
  },
  joinUrl: (code: string) => `http://localhost/app/salas?codigo=${code}`,
}));
vi.mock('./api/hooks', () => ({
  useMe: () => ({ data: { profile: { id: 3, firstName: 'Alumno3', lastName: 'C.', displayName: null }, organizer: false, organizationId: null, roles: [] } }),
}));

import { roomsApi } from './api/rooms';
import { BoardGrid, boardStatus, pageCount, pageOf } from './components/room/BoardGrid';
import { OrganizerRoom, OrganizerRooms } from './pages/OrganizerRooms';
import { RoomPage, Rooms } from './pages/Rooms';

const renderAt = (path: string, pattern: string, ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={pattern} element={ui} />
        <Route path="/app/partidas/:id" element={<p>Partida en curso</p>} />
        <Route path="/club/salas/:id" element={<p>Panel del organizador</p>} />
      </Routes>
    </MemoryRouter>
  </QueryClientProvider>,
);

describe('salas de juego', () => {
  it('pagina los tableros de a 4 por pantalla', () => {
    const items = [1, 2, 3, 4, 5, 6];
    expect(pageOf(items, 0)).toEqual([1, 2, 3, 4]);
    expect(pageOf(items, 1)).toEqual([5, 6]);
    expect(pageCount(items)).toBe(2);
    expect(pageCount([])).toBe(1);
    expect(pageCount([1, 2, 3, 4])).toBe(1);
  });

  it('describe cada tablero en palabras', () => {
    const empty: RoomBoard = { boardNo: 2, white: null, black: null, game: null };
    const ready: RoomBoard = { ...empty, white: { playerId: 1, name: 'A' }, black: { playerId: 2, name: 'B' } };
    expect(boardStatus(empty)).toBe('Esperando jugadores');
    expect(boardStatus(ready)).toBe('Listo para empezar');
    expect(boardStatus({ ...ready, game: fx.game(1, 'ACTIVE') })).toBe('En juego · jugada 1 · mueven negras');
    expect(boardStatus({ ...ready, game: fx.game(1, 'FINISHED') })).toBe('Terminada · Ganan blancas por abandono');
  });

  it('cuadrícula: 4 tableros por pantalla y paginación accesible', async () => {
    const { container } = render(<BoardGrid boards={fx.room.boards} />);
    expect(screen.getAllByRole('article')).toHaveLength(4);
    expect(screen.getByRole('img', { name: 'Tablero 1: Alumno1 A. (blancas) contra Alumno2 B. (negras), en juego · jugada 1 · mueven negras' })).toBeInTheDocument();
    expect(screen.getByText('Tableros 1–4 de 6')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Siguientes →' }));
    expect(screen.getAllByRole('article')).toHaveLength(2);
    expect(screen.getByRole('article', { name: 'Tablero 6' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Siguientes →' })).toBeDisabled();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('organizador: código, jugadores, asignar e iniciar un tablero', async () => {
    const { container } = renderAt('/club/salas/9', '/club/salas/:id', <OrganizerRoom />);
    expect(await screen.findByText('AB3K9Q')).toBeInTheDocument();
    expect(screen.getAllByText(/· espectador/, { selector: 'span' })).toHaveLength(2); // los alumnos 3 y 4 miran
    // Tablero 2: el 3 con blancas y el 4 con negras
    fireEvent.change(screen.getByLabelText('Blancas tablero 2'), { target: { value: '3' } });
    fireEvent.change(screen.getByLabelText('Negras tablero 2'), { target: { value: '4' } });
    fireEvent.click(screen.getAllByRole('button', { name: 'Guardar puestos' })[0]);
    await vi.waitFor(() => expect(roomsApi.assign).toHaveBeenCalledWith(9, 2, { whitePlayerId: 3, blackPlayerId: 4 }));
    // El tablero 1 está en juego: no se puede tocar
    expect(screen.getByText('Partida en curso')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar todos los tableros listos' }));
    await vi.waitFor(() => expect(roomsApi.startAll).toHaveBeenCalledWith(9));
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getByRole('button', { name: 'Pantalla completa (proyector)' }));
    expect(screen.getByRole('dialog', { name: 'Clase 4°B: tableros en pantalla completa' })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('organizador: abrir una sala nueva', async () => {
    vi.mocked(roomsApi.create).mockResolvedValue(fx.room);
    const { container } = renderAt('/club/salas', '/club/salas', <OrganizerRooms />);
    fireEvent.change(screen.getByLabelText('Nombre de la sala'), { target: { value: 'Clase 4°B' } });
    fireEvent.change(screen.getByLabelText('Tableros'), { target: { value: '6' } });
    expect(screen.getByText(/12 jugadores juegan a la vez/)).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getByRole('button', { name: 'Abrir sala' }));
    await vi.waitFor(() => expect(roomsApi.create).toHaveBeenCalled());
    expect(vi.mocked(roomsApi.create).mock.calls[0][0]).toMatchObject({ name: 'Clase 4°B', boards: 6 });
    expect(await screen.findByText('Panel del organizador')).toBeInTheDocument();
  });

  it('jugador: entra con el código del QR', async () => {
    const { container } = renderAt('/app/salas?codigo=ab3k9q', '/app/salas', <Rooms />);
    expect(screen.getByLabelText('Código de la sala')).toHaveValue('ab3k9q');
    expect(await screen.findByText('Clase 4°B')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    await vi.waitFor(() => expect(roomsApi.join).toHaveBeenCalled());
    expect(vi.mocked(roomsApi.join).mock.calls[0][0]).toBe('ab3k9q');
  });

  it('jugador: espera como espectador y pasa solo a su partida cuando empieza', async () => {
    vi.mocked(roomsApi.get).mockResolvedValueOnce({ ...fx.room, organizer: false });
    const first = renderAt('/app/salas/9', '/app/salas/:id', <RoomPage />);
    expect(await screen.findByText(/Estás mirando como espectador/)).toBeInTheDocument();
    expect(await axe(first.container)).toHaveNoViolations();
    first.unmount();

    const seated = { ...fx.room, organizer: false, myBoard: 1, myGameId: 50 };
    vi.mocked(roomsApi.get).mockResolvedValueOnce(seated);
    renderAt('/app/salas/9', '/app/salas/:id', <RoomPage />);
    expect(await screen.findByText('Partida en curso')).toBeInTheDocument();
  });
});
