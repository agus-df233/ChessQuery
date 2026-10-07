import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as axeMatchers from 'vitest-axe/matchers';
import type { RoundView, StandingView, TournamentDetail, TournamentView } from './api/tournamentTypes';

expect.extend(axeMatchers);

/** Vistas de torneos (pública, jugador y organizador) con la API simulada: render, acciones clave y axe. */
const fx = vi.hoisted(() => {
  const t: TournamentView = {
    id: 5, name: 'Abierto de Primavera', city: 'Santiago', region: 'RM', startDate: '2026-10-03', endDate: null,
    format: 'SWISS', roundsPlanned: 3, currentRound: 1, timeControl: '60+30', baseMinutes: 60, incrementSeconds: 30, category: 'CLASSICAL', rated: true, status: 'IN_PROGRESS',
    organizationId: 3, playerCount: 3,
  };
  const ref = (id: number, name: string, title: string | null = null) => ({ playerId: id, name, title, rating: 2000 - id });
  const detail: TournamentDetail = {
    tournament: t,
    players: [{ startRank: 1, player: ref(11, 'Ana Soto', 'WFM'), clubName: 'Club Torre' },
              { startRank: 2, player: ref(12, 'Luis Paz'), clubName: null }, { startRank: 3, player: ref(17, 'Vicente M.'), clubName: null }],
  };
  const round: RoundView = { number: 1, complete: false, boards: [
    { board: 1, white: ref(11, 'Ana Soto', 'WFM'), black: ref(12, 'Luis Paz'), result: null, resultLabel: null },
    { board: 2, white: ref(17, 'Vicente M.'), black: null, result: 'BYE', resultLabel: 'bye' }] };
  const standings: StandingView[] = [
    { position: 1, player: ref(17, 'Vicente M.'), points: 1, buchholzCut1: 0, buchholz: 0, sonnebornBerger: 0, wins: 0, played: 0 },
    { position: 2, player: ref(11, 'Ana Soto', 'WFM'), points: 0, buchholzCut1: 0, buchholz: 0, sonnebornBerger: 0, wins: 0, played: 0 }];
  return { t, detail, round, standings };
});

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ isAuthenticated: true, isLoading: false, user: { access_token: 't' }, signinRedirect: vi.fn(), signoutRedirect: vi.fn() }),
}));
vi.mock('./api/tournaments', () => ({
  publicTournamentsApi: {
    list: vi.fn().mockResolvedValue([{ ...fx.t, id: 6, name: 'Relámpago del sábado', status: 'OPEN', currentRound: 0 }]),
    detail: vi.fn().mockResolvedValue(fx.detail),
    rounds: vi.fn().mockResolvedValue([fx.round]),
    standings: vi.fn().mockResolvedValue(fx.standings),
    calendar: vi.fn().mockResolvedValue([{ federationTournamentId: '901', title: 'Nacional Juvenil', city: 'Temuco', region: null,
      clubName: null, startDate: '2026-11-10', endDate: null, type: 'Suizo', rounds: 7, timeControl: '90+30', category: null,
      ratedNational: true, ratedFide: true }]),
  },
  tournamentsApi: {
    mine: vi.fn().mockResolvedValue({ organized: [fx.t], registered: [fx.t] }),
    join: vi.fn().mockResolvedValue(fx.detail), leave: vi.fn(), create: vi.fn(), update: vi.fn(), register: vi.fn(),
    unregister: vi.fn(), nextRound: vi.fn().mockResolvedValue(fx.round), setResult: vi.fn().mockResolvedValue(fx.round),
    finish: vi.fn(), trf: vi.fn(),
  },
}));
vi.mock('./api/users', () => ({
  usersApi: { me: vi.fn().mockResolvedValue({ profile: { id: 11, firstName: 'Ana', lastName: 'Soto', displayName: null, email: 'a@x.cl' }, organizationId: 3, organizer: true, roles: [] }) },
  organizationsApi: { roster: vi.fn().mockResolvedValue([]) },
}));

import { tournamentsApi } from './api/tournaments';
import { PublicTournamentDetail, PublicTournaments } from './pages/PublicTournaments';
import { Tournaments, myPairing } from './pages/Tournaments';
import { OrganizerTournament, OrganizerTournaments } from './pages/OrganizerTournaments';

const renderAt = (path: string, pattern: string, ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[path]}><Routes><Route path={pattern} element={ui} /></Routes></MemoryRouter>
  </QueryClientProvider>,
);

describe('torneos: vistas', () => {
  it('pública: listado de clubes y calendario de la Federación', async () => {
    const { container } = renderAt('/torneos', '/torneos', <PublicTournaments />);
    await screen.findByText('Relámpago del sábado');
    await screen.findByText('Nacional Juvenil');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pública: tabla con desempates, ronda en juego con bye e inscritos', async () => {
    const { container } = renderAt('/torneos/5', '/torneos/:id', <PublicTournamentDetail />);
    await screen.findByText('Clasificación');
    await screen.findByText('Ronda 1 · en juego');
    expect(screen.getByText('descansa (bye)')).toBeInTheDocument();
    expect(screen.getAllByText('WFM Ana Soto').length).toBeGreaterThan(0);
    expect(await axe(container)).toHaveNoViolations();
  });

  it('jugador: se inscribe en un torneo abierto', async () => {
    const { container } = renderAt('/app/torneos', '/app/torneos', <Tournaments />);
    fireEvent.click(await screen.findByRole('button', { name: 'Inscribirme' }));
    await vi.waitFor(() => expect(tournamentsApi.join).toHaveBeenCalledWith(6));
    expect(await axe(container)).toHaveNoViolations();
  });

  it('organizador: carga el resultado de una mesa y exporta TRF', async () => {
    const { container } = renderAt('/club/torneos/5', '/club/torneos/:id', <OrganizerTournament />);
    const select = await screen.findByLabelText('Resultado mesa 1');
    fireEvent.change(select, { target: { value: 'DRAW' } });
    await vi.waitFor(() => expect(tournamentsApi.setResult).toHaveBeenCalledWith(5, 1, 1, 'DRAW'));
    expect(screen.getByRole('button', { name: 'Generar ronda 2' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Exportar TRF' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('organizador: formulario de nuevo torneo', async () => {
    const { container } = renderAt('/club/torneos', '/club/torneos', <OrganizerTournaments />);
    fireEvent.change(screen.getByLabelText('Nombre del torneo'), { target: { value: 'Blitz' } });
    // El ritmo estructurado define qué ELO actualiza el torneo: 60+30 por defecto (clásica), 3+2 relámpago
    expect(screen.getByText('Clásica 60+30: actualiza el ELO ChessQuery de ese ritmo')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Minutos por jugador'), { target: { value: '3' } });
    fireEvent.change(screen.getByLabelText('Incremento (segundos)'), { target: { value: '2' } });
    expect(screen.getByText('Relámpago 3+2: actualiza el ELO ChessQuery de ese ritmo')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getByRole('button', { name: 'Crear torneo' }));
    await vi.waitFor(() => expect(tournamentsApi.create).toHaveBeenCalled());
    expect(vi.mocked(tournamentsApi.create).mock.calls[0][0]).toMatchObject({ name: 'Blitz', baseMinutes: 3, incrementSeconds: 2 });
  });

  it('jugador: ve su mesa de la ronda en curso', () => {
    expect(myPairing(fx.round, 12)).toBe('Ronda 1 · mesa 1: juegas con negras contra Ana Soto.');
    expect(myPairing(fx.round, 11)).toBe('Ronda 1 · mesa 1: juegas con blancas contra Luis Paz.');
    expect(myPairing(fx.round, 17)).toBe('Ronda 1: descansas (bye, suma 1 punto).');
    expect(myPairing(fx.round, 99)).toBe('Ronda 1: no tienes mesa en esta ronda.');
  });
});
