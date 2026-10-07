import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as axeMatchers from 'vitest-axe/matchers';
import type { RegistrationView, RoundView, StandingView, TournamentDetail, TournamentView } from './api/tournamentTypes';

expect.extend(axeMatchers);

/** Vistas de torneos (pública, jugador y organizador) con la API simulada: render, acciones clave y axe. */
const fx = vi.hoisted(() => {
  const t: TournamentView = {
    id: 5, name: 'Abierto de Primavera', city: 'Santiago', region: 'RM', startDate: '2026-10-03', endDate: null,
    format: 'SWISS', roundsPlanned: 3, currentRound: 1, timeControl: '60+30', baseMinutes: 60, incrementSeconds: 30, category: 'CLASSICAL', rated: true, status: 'IN_PROGRESS',
    organizationId: 3, playerCount: 3, pendingCount: 0, waitlistCount: 0, registrationClosesAt: null, maxPlayers: null,
    requiresApproval: false, minRating: null, maxRating: null, checkinRequired: false,
  };
  const ref = (id: number, name: string, title: string | null = null) => ({ playerId: id, name, title, rating: 2000 - id });
  const detail: TournamentDetail = {
    tournament: t,
    players: [{ startRank: 1, player: ref(11, 'Ana Soto', 'WFM'), clubName: 'Club Torre', withdrawnFromRound: null },
              { startRank: 2, player: ref(12, 'Luis Paz'), clubName: null, withdrawnFromRound: null },
              { startRank: 3, player: ref(17, 'Vicente M.'), clubName: null, withdrawnFromRound: null }],
  };
  const round: RoundView = { number: 1, complete: false, boards: [
    { board: 1, white: ref(11, 'Ana Soto', 'WFM'), black: ref(12, 'Luis Paz'), result: null, resultLabel: null },
    { board: 2, white: ref(17, 'Vicente M.'), black: null, result: 'BYE', resultLabel: 'bye' }] };
  const standings: StandingView[] = [
    { position: 1, player: ref(17, 'Vicente M.'), points: 1, buchholzCut1: 0, buchholz: 0, sonnebornBerger: 0, wins: 0, played: 0 },
    { position: 2, player: ref(11, 'Ana Soto', 'WFM'), points: 0, buchholzCut1: 0, buchholz: 0, sonnebornBerger: 0, wins: 0, played: 0 }];
  const reg = (playerId: number, name: string, status: RegistrationView['status'], extra: Partial<RegistrationView> = {}): RegistrationView => ({
    playerId, name, title: null, clubName: null, seedRating: 2000 - playerId, status, checkedInAt: null,
    withdrawnFromRound: null, checkinCode: `cod-${playerId}`, createdAt: '2026-10-01T12:00:00Z', ...extra });
  const regs: RegistrationView[] = [
    reg(11, 'Ana Soto', 'CONFIRMED', { title: 'WFM', checkedInAt: '2026-10-03T13:00:00Z' }), reg(12, 'Luis Paz', 'CONFIRMED'),
    reg(13, 'Pedro P.', 'PENDING'), reg(14, 'Rosa R.', 'WAITLIST'), reg(17, 'Vicente M.', 'WITHDRAWN', { withdrawnFromRound: 2 })];
  const openT: TournamentView = { ...t, status: 'OPEN', currentRound: 0, checkinRequired: true, maxPlayers: 3, pendingCount: 1, waitlistCount: 1, playerCount: 2 };
  return { t, detail, round, standings, regs, openT };
});
vi.mock('./components/tournament/QrScanner', () => ({
  QrScanner: ({ onCode }: { onCode: (c: string) => void }) => <button type="button" onClick={() => onCode('cod-12')}>Simular lectura del QR</button>,
}));

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ isAuthenticated: true, isLoading: false, user: { access_token: 't' }, signinRedirect: vi.fn(), signoutRedirect: vi.fn() }),
}));
vi.mock('./api/tournaments', () => ({
  publicTournamentsApi: {
    list: vi.fn().mockResolvedValue([{ ...fx.t, id: 6, name: 'Relámpago del sábado', status: 'OPEN', currentRound: 0 }]),
    detail: vi.fn().mockResolvedValue(fx.detail),
    rounds: vi.fn().mockResolvedValue([fx.round]),
    standings: vi.fn().mockResolvedValue(fx.standings),
    live: vi.fn().mockResolvedValue({ version: 3, detail: fx.detail, rounds: [fx.round], standings: fx.standings }),
    waitLive: vi.fn(() => new Promise(() => undefined)),
    calendar: vi.fn().mockResolvedValue([{ federationTournamentId: '901', title: 'Nacional Juvenil', city: 'Temuco', region: null,
      clubName: null, startDate: '2026-11-10', endDate: null, type: 'Suizo', rounds: 7, timeControl: '90+30', category: null,
      ratedNational: true, ratedFide: true }]),
  },
  tournamentsApi: {
    mine: vi.fn().mockResolvedValue({ organized: [fx.t], registered: [fx.t] }),
    join: vi.fn().mockResolvedValue(fx.detail), leave: vi.fn(), create: vi.fn(), update: vi.fn(), register: vi.fn(),
    unregister: vi.fn(), nextRound: vi.fn().mockResolvedValue(fx.round), setResult: vi.fn().mockResolvedValue(fx.round),
    finish: vi.fn(), trf: vi.fn(),
    registrations: vi.fn().mockResolvedValue(fx.regs), registerAll: vi.fn().mockResolvedValue([]),
    approve: vi.fn().mockResolvedValue(fx.detail), withdraw: vi.fn().mockResolvedValue(fx.detail),
    checkin: vi.fn().mockResolvedValue({ registration: fx.regs[1], alreadyCheckedIn: false }),
    checkinManually: vi.fn().mockResolvedValue({ registration: fx.regs[1], alreadyCheckedIn: false }),
    undoCheckin: vi.fn().mockResolvedValue(fx.regs[0]),
    myRegistration: vi.fn().mockResolvedValue({ ...fx.regs[2] }),
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
import { OrganizerCheckin } from './pages/OrganizerCheckin';
import { OrganizerCredentials } from './pages/OrganizerCredentials';
import { TournamentPage } from './pages/Tournaments';
import { TournamentScreen, panelsOf } from './pages/TournamentScreen';
import { publicTournamentsApi } from './api/tournaments';

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
    fireEvent.change(screen.getByLabelText('Cupo de jugadores'), { target: { value: '16' } });
    fireEvent.click(screen.getByLabelText('Apruebo cada inscripción'));
    expect(screen.getByText('Relámpago 3+2: actualiza el ELO ChessQuery de ese ritmo')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getByRole('button', { name: 'Crear torneo' }));
    await vi.waitFor(() => expect(tournamentsApi.create).toHaveBeenCalled());
    expect(vi.mocked(tournamentsApi.create).mock.calls[0][0])
      .toMatchObject({ name: 'Blitz', baseMinutes: 3, incrementSeconds: 2, maxPlayers: 16, requiresApproval: true });
  });

  it('organizador: inscripciones por estado; aprobar y rechazar con el torneo abierto', async () => {
    vi.mocked(publicTournamentsApi.detail).mockResolvedValue({ ...fx.detail, tournament: fx.openT }); // sigue abierto al recargar
    vi.mocked(publicTournamentsApi.live).mockResolvedValue({ version: 1, detail: { ...fx.detail, tournament: fx.openT }, rounds: [], standings: [] });
    const { container } = renderAt('/club/torneos/5', '/club/torneos/:id', <OrganizerTournament />);
    expect(await screen.findByText('Esperan tu aprobación (1)')).toBeInTheDocument();
    expect(screen.getByText('Lista de espera (entran solos si se libera un cupo) (1)')).toBeInTheDocument();
    expect(screen.getByText(/retirado desde la ronda 2/)).toBeInTheDocument();
    expect(screen.getByText('Inscripciones · 3/3 cupos')).toBeInTheDocument();
    expect(screen.getByText(/Inscripción: Cupo 3 · acreditación con QR/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Acreditación con QR' })).toHaveAttribute('href', '/club/torneos/5/acreditacion');
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getAllByRole('button', { name: 'Rechazar' })[0]);
    await vi.waitFor(() => expect(tournamentsApi.unregister).toHaveBeenCalledWith(5, 13));
    fireEvent.click(screen.getAllByRole('button', { name: 'Aprobar' })[0]);
    await vi.waitFor(() => expect(tournamentsApi.approve).toHaveBeenCalledWith(5, 13));
    vi.mocked(publicTournamentsApi.detail).mockResolvedValue(fx.detail);
    vi.mocked(publicTournamentsApi.live).mockResolvedValue({ version: 3, detail: fx.detail, rounds: [fx.round], standings: fx.standings });
  });

  it('organizador: durante el torneo retira a un jugador', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderAt('/club/torneos/5', '/club/torneos/:id', <OrganizerTournament />);
    fireEvent.click((await screen.findAllByRole('button', { name: 'Retirar' }))[1]);
    await vi.waitFor(() => expect(tournamentsApi.withdraw).toHaveBeenCalledWith(5, 12));
  });

  it('organizador: acreditación con el lector, con el código y desde la lista', async () => {
    const { container } = renderAt('/club/torneos/5/acreditacion', '/club/torneos/:id/acreditacion', <OrganizerCheckin />);
    expect(await screen.findByText('1 de 2')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Simular lectura del QR' }));
    await vi.waitFor(() => expect(tournamentsApi.checkin).toHaveBeenCalledWith(5, 'cod-12'));
    expect(await screen.findByText('Luis Paz acreditado')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Código de acreditación'), { target: { value: 'cod-11' } });
    fireEvent.click(screen.getByRole('button', { name: 'Acreditar' }));
    await vi.waitFor(() => expect(tournamentsApi.checkin).toHaveBeenCalledWith(5, 'cod-11'));
    fireEvent.click(screen.getByRole('button', { name: 'Acreditar a Luis Paz' }));
    await vi.waitFor(() => expect(tournamentsApi.checkinManually).toHaveBeenCalledWith(5, 12));
    fireEvent.click(screen.getByRole('button', { name: 'Deshacer acreditación de Ana Soto' }));
    await vi.waitFor(() => expect(tournamentsApi.undoCheckin).toHaveBeenCalledWith(5, 11));
    expect(await axe(container)).toHaveNoViolations();
  });

  it('organizador: credenciales con QR solo para los confirmados', async () => {
    const { container } = renderAt('/club/torneos/5/credenciales', '/club/torneos/:id/credenciales', <OrganizerCredentials />);
    expect(await screen.findByRole('article', { name: 'Credencial de Ana Soto' })).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(2);
    expect(await axe(container)).toHaveNoViolations();
  });

  it('jugador: ve el estado de su inscripción y su QR para acreditarse', async () => {
    vi.mocked(publicTournamentsApi.detail).mockResolvedValue({ ...fx.detail, tournament: fx.openT });
    const first = renderAt('/app/torneos/5', '/app/torneos/:id', <TournamentPage />);
    expect(await screen.findByText('Tu inscripción espera la aprobación del organizador.')).toBeInTheDocument();
    first.unmount();
    vi.mocked(tournamentsApi.myRegistration).mockResolvedValueOnce(fx.regs[1]);
    const { container } = renderAt('/app/torneos/5', '/app/torneos/:id', <TournamentPage />);
    expect(await screen.findByRole('img', { name: 'Mi QR de acreditación' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
    vi.mocked(publicTournamentsApi.detail).mockResolvedValue(fx.detail);
  });

  it('pantalla de la sala: emparejamientos en grande, QR para seguir y rotación que se puede pausar', async () => {
    const { container } = renderAt('/torneos/5/pantalla', '/torneos/:id/pantalla', <TournamentScreen />);
    expect(await screen.findByText('Ronda 1 · emparejamientos')).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'QR para seguir el torneo desde el celular' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Pausar rotación' }));
    expect(screen.getByRole('button', { name: 'Reanudar rotación' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pantalla de la sala: un panel por cada 12 filas de mesas y de tabla', () => {
    const live = { version: 1, detail: fx.detail, rounds: [fx.round], standings: fx.standings };
    expect(panelsOf(live)).toEqual([{ kind: 'boards', page: 0 }, { kind: 'standings', page: 0 }]);
    const many = { ...fx.round, boards: Array.from({ length: 13 }, (_, i) => ({ ...fx.round.boards[0], board: i + 1 })) };
    expect(panelsOf({ ...live, rounds: [many], standings: [] })).toEqual([{ kind: 'boards', page: 0 }, { kind: 'boards', page: 1 }]);
    expect(panelsOf({ ...live, rounds: [], standings: [] })).toEqual([]);
  });

  it('apoderado: sigue a un jugador sin cuenta y ve su mesa de la ronda en curso', async () => {
    localStorage.clear();
    const { container } = renderAt('/torneos/5', '/torneos/:id', <PublicTournamentDetail />);
    fireEvent.change(await screen.findByLabelText('Busca a tu hijo, a un amigo o a cualquier jugador'), { target: { value: 'luis' } });
    fireEvent.click(screen.getByRole('button', { name: 'Seguir a Luis Paz' }));
    expect(await screen.findByText(/mesa 1, con negras contra Ana Soto · en juego/)).toBeInTheDocument();
    expect(localStorage.getItem('cq-follow-5')).toBe('12');
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getByRole('button', { name: 'Dejar de seguir' }));
    expect(localStorage.getItem('cq-follow-5')).toBeNull();
  });

  it('jugador: ve su mesa de la ronda en curso', () => {
    expect(myPairing(fx.round, 12)).toBe('Ronda 1 · mesa 1: juegas con negras contra Ana Soto.');
    expect(myPairing(fx.round, 11)).toBe('Ronda 1 · mesa 1: juegas con blancas contra Luis Paz.');
    expect(myPairing(fx.round, 17)).toBe('Ronda 1: descansas (bye, suma 1 punto).');
    expect(myPairing(fx.round, 99)).toBe('Ronda 1: no tienes mesa en esta ronda.');
  });
});
