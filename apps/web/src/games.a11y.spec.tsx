import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as axeMatchers from 'vitest-axe/matchers';
import type { GameView } from './api/gameTypes';

expect.extend(axeMatchers);

/** Partidas: tablero jugable, relojes, desafíos y long polling con la API simulada. */
const fx = vi.hoisted(() => {
  const game: GameView = {
    id: 9, status: 'ACTIVE', challengerId: 7, initialSeconds: 180, incrementSeconds: 2, rated: true,
    white: { playerId: 7, name: 'Ana Soto', ratingBefore: 1500, ratingAfter: null, clockMs: 175_000 },
    black: { playerId: 8, name: 'Luis P.', ratingBefore: 1600, ratingAfter: null, clockMs: 9_500 },
    fen: 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', moves: [], san: [], ply: 0, sideToMove: 'WHITE',
    drawOfferBy: 8, result: null, termination: null, terminationLabel: null, version: 3,
    createdAt: '2026-10-01T15:00:00Z', finishedAt: null,
  };
  const finished: GameView = {
    ...game, id: 10, status: 'FINISHED', fen: 'r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4', result: 'WHITE_WINS', termination: 'CHECKMATE', terminationLabel: 'jaque mate',
    white: { ...game.white, ratingAfter: 1513 }, black: { ...game.black, ratingAfter: 1574 }, drawOfferBy: null,
    san: ['e4', 'e5', 'Bc4', 'Nc6', 'Qh5', 'Nf6', 'Qxf7#'], moves: ['e2e4', 'e7e5', 'f1c4', 'b8c6', 'd1h5', 'g8f6', 'h5f7'], ply: 7,
  };
  return { game, finished };
});

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ isAuthenticated: true, isLoading: false, user: { access_token: 't' }, signinRedirect: vi.fn(), signoutRedirect: vi.fn() }),
}));
vi.mock('./api/games', () => ({
  gamesApi: {
    get: vi.fn((id: number) => Promise.resolve(id === 10 ? fx.finished : fx.game)),
    waitChange: vi.fn(() => new Promise(() => undefined)),
    move: vi.fn().mockResolvedValue({ ...fx.game, version: 4, sideToMove: 'BLACK', moves: ['e2e4'], san: ['e4'] }),
    acceptDraw: vi.fn().mockResolvedValue(fx.finished), declineDraw: vi.fn(), offerDraw: vi.fn(), resign: vi.fn(),
    accept: vi.fn().mockResolvedValue(fx.game), decline: vi.fn(), cancel: vi.fn(),
    mine: vi.fn().mockResolvedValue({ incoming: [{ ...fx.game, id: 11, status: 'PENDING', challengerId: 8 }], outgoing: [],
      active: [fx.game], finished: [fx.finished] }),
    challenge: vi.fn(),
  },
  pgnUrl: (id: number) => `/api/public/games/${id}/pgn`,
}));
vi.mock('./api/users', () => ({
  usersApi: { me: vi.fn().mockResolvedValue({ profile: { id: 7, firstName: 'Ana', lastName: 'Soto', displayName: null }, organizationId: null, organizer: false, roles: [] }) },
  friendsApi: { list: vi.fn().mockResolvedValue([{ playerId: 8, firstName: 'Luis', lastName: 'Paz', clubName: null, eloNational: 1600, eloPlatform: null, since: '' }]) },
}));

import { gamesApi } from './api/games';
import { GamePage } from './pages/GamePage';
import { Games } from './pages/Games';
import { formatClock } from './components/game/ChessClock';
import { piecesFromFen, squareLabel } from './components/game/PlayBoard';
import { ratingDelta, resultFor } from './components/game/labels';

const renderAt = (path: string, pattern: string, ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[path]}><Routes><Route path={pattern} element={ui} /></Routes></MemoryRouter>
  </QueryClientProvider>,
);

describe('partidas', () => {
  it('juego una jugada con dos clics y espero cambios por long polling', async () => {
    const { container } = renderAt('/app/partidas/9', '/app/partidas/:id', <GamePage />);
    fireEvent.click(await screen.findByRole('button', { name: 'e2, peón blanco' }));
    fireEvent.click(screen.getByRole('button', { name: 'e4, vacía' }));
    await vi.waitFor(() => expect(gamesApi.move).toHaveBeenCalledWith(9, 'e2e4'));
    expect(gamesApi.waitChange).toHaveBeenCalledWith(9, 3, expect.anything());
    // Es el reloj que corre: según la carga de la máquina puede haber avanzado un par de segundos
    expect(screen.getByRole('timer', { name: 'Reloj de Ana Soto' }).textContent).toMatch(/^2:5[3-5]$/);
    expect(screen.getByText('Tu rival ofrece tablas.')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('no puedo tomar piezas del rival y acepto las tablas ofrecidas', async () => {
    renderAt('/app/partidas/9', '/app/partidas/:id', <GamePage />);
    fireEvent.click(await screen.findByRole('button', { name: 'e7, peón negro' }));
    expect(screen.getByRole('button', { name: 'e7, peón negro' })).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(screen.getByRole('button', { name: 'Aceptar tablas' }));
    await vi.waitFor(() => expect(gamesApi.acceptDraw).toHaveBeenCalled());
  });

  it('partida terminada: resultado, cambio de rating, PGN y tablero bloqueado', async () => {
    const { container } = renderAt('/app/partidas/10', '/app/partidas/:id', <GamePage />);
    await screen.findByText('Ganaste · jaque mate');
    expect(screen.getByText(/Ana Soto: 1513 \(\+13\)/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Descargar PGN' })).toHaveAttribute('href', '/api/public/games/10/pgn');
    expect(screen.getByRole('button', { name: 'f7, dama blanca' })).toBeDisabled();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('mis partidas: acepto un desafío y veo historial', async () => {
    const { container } = renderAt('/app/partidas', '/app/partidas', <Games />);
    fireEvent.click(await screen.findByRole('button', { name: 'Aceptar' }));
    await vi.waitFor(() => expect(gamesApi.accept).toHaveBeenCalledWith(11));
    expect(screen.getByText('Historial')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Desafiar' }));
    expect(screen.getByRole('group', { name: 'Desafiar a Luis Paz' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('utilidades: FEN, reloj y resultados', () => {
    const p = piecesFromFen(fx.game.fen);
    expect(p.e1).toBe('K');
    expect(p.d8).toBe('q');
    expect(p.e4).toBeUndefined();
    expect(formatClock(65_000)).toBe('1:05');
    expect(formatClock(9_450)).toBe('0:09.4');
    expect(formatClock(-5)).toBe('0:00.0');
    expect(ratingDelta(1600, 1574)).toBe('−26');
    expect(squareLabel('d8', 'q')).toBe('d8, dama negra');
    expect(squareLabel('a1', 'R')).toBe('a1, torre blanca');
    expect(squareLabel('g1', 'N')).toBe('g1, caballo blanco');
    expect(ratingDelta(null, 1)).toBeNull();
    expect(resultFor('BLACK_WINS', 7, fx.game)).toBe('Perdiste');
    expect(resultFor('DRAW')).toBe('Tablas');
    expect(resultFor('WHITE_WINS', 99, fx.game)).toBe('Ganan blancas');
  });
});
