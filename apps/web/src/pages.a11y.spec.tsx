import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as axeMatchers from 'vitest-axe/matchers';
import type { Me, Organization, Profile } from './api/types';

expect.extend(axeMatchers);

/**
 * Gate de accesibilidad y de render de cada página con la API y la sesión simuladas.
 * Si un cambio rompe el render o introduce una violación de axe, falla acá.
 */
const fx = vi.hoisted(() => {
  const profile: Profile = {
    id: 7, firstName: 'Ana', lastName: 'Soto', displayName: null, email: 'ana@x.cl', rut: '1-9', birthDate: null, gender: null,
    region: 'RM', country: { id: 1, isoCode: 'CHL', name: 'Chile', fideFederation: 'CHI' }, club: null,
    fideId: '123', federationId: null, lichessUsername: 'ana', chesscomUsername: null,
    ratings: { national: 1500, fideStandard: null, fideRapid: null, fideBlitz: null, platformBullet: null, platformBlitz: 1420, platformRapid: null, platformClassical: null, lichessBullet: null,
      lichessBlitz: 1600, lichessRapid: null, lichessClassical: null, chesscomBullet: null, chesscomBlitz: null, chesscomRapid: null, chesscomDaily: null },
    currentTitle: null, ageCategory: 'ADULTO', enrichmentSource: null, enrichedAt: null,
    provisional: false, createdByOrganizerId: null, active: true, tags: [], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
  };
  const me: Me = { profile, organizationId: 3, organizer: true, roles: [] };
  const org: Organization = { id: 3, name: 'Club Torre', city: 'Santiago', description: null, logoUrl: null, plan: 'FREE', rosterCount: 1, maxRosterPlayers: 50, maxActiveTournaments: 3 };
  const points = [{ recordedAt: '2026-01-01T00:00:00Z', rating: 1480, previous: null, delta: null, source: 'GAME' },
                  { recordedAt: '2026-02-01T00:00:00Z', rating: 1500, previous: 1480, delta: 20, source: 'GAME' }];
  return { profile, me, org, points };
});
const { profile, me, org, points } = fx;

vi.mock('react-oidc-context', () => ({
  useAuth: () => ({ isAuthenticated: true, isLoading: false, user: { access_token: 't' }, signinRedirect: vi.fn(), signoutRedirect: vi.fn() }),
}));
vi.mock('./api/users', () => ({
  usersApi: {
    me: vi.fn().mockResolvedValue(fx.me),
    myRatingHistory: vi.fn().mockResolvedValue(fx.points),
    ratingHistory: vi.fn().mockResolvedValue(fx.points),
    publicProfile: vi.fn().mockResolvedValue({ ...fx.profile, email: undefined, rut: undefined }),
    search: vi.fn().mockResolvedValue([{ id: 8, firstName: 'Luis', lastName: 'Paz', currentTitle: 'FM', clubName: null, countryIso: 'CHL', fideId: null, eloNational: 1700, eloFideStandard: null, platform: { bullet: null, blitz: null, rapid: null, classical: null } }]),
    ranking: vi.fn().mockResolvedValue([{ position: 1, playerId: 8, firstName: 'Luis', lastName: 'Paz', currentTitle: null, region: 'RM', clubName: 'X', ratingType: 'NATIONAL', rating: 1700, eloNational: 1700, eloFideStandard: null, ageCategory: 'SUB_14' }]),
    countries: vi.fn().mockResolvedValue([]), clubs: vi.fn().mockResolvedValue([]),
    updateMyProfile: vi.fn(), syncExternalRatings: vi.fn(), linkFederation: vi.fn(), claim: vi.fn(),
    claimSuggestions: vi.fn().mockResolvedValue([{ ...fx.profile, id: 44, fideId: '3400001', currentTitle: 'WFM', email: undefined, rut: undefined }]),
  },
  publicApi: {
    ranking: vi.fn().mockResolvedValue([{ position: 1, playerId: 12, firstName: 'Vicente', lastName: 'M.', currentTitle: null, region: null, clubName: null, ratingType: 'FIDE_STANDARD', rating: 1923, eloNational: null, eloFideStandard: 1923, ageCategory: 'SUB_12' }]),
  },
  organizationsApi: {
    mine: vi.fn().mockResolvedValue(fx.org),
    roster: vi.fn().mockResolvedValue([{ ...fx.profile, id: 9, firstName: 'Pedro', lastName: 'Rojas', provisional: true, tags: ['sub12'] }]),
    create: vi.fn(), update: vi.fn(), addToRoster: vi.fn(), updateTags: vi.fn(), deactivate: vi.fn(),
    importRoster: vi.fn().mockResolvedValue({ created: 2, duplicates: 1, errors: 0, rows: [
      { row: 1, outcome: 'CREATED', playerId: 101, error: null, message: null },
      { row: 2, outcome: 'CREATED', playerId: 102, error: null, message: null },
      { row: 3, outcome: 'DUPLICATE', playerId: null, error: 'EMAIL_TAKEN', message: 'Ya existe' }] }),
    invite: vi.fn().mockResolvedValue({ playerId: 9, token: 'tokPedro', expiresAt: '2026-11-07T12:00:00Z' }),
  },
  claimApi: {
    preview: vi.fn().mockResolvedValue({ firstName: 'Pedro', lastName: 'R.', organizationName: 'Club Torre' }),
    claim: vi.fn().mockResolvedValue({}),
  },
  claimUrl: (token: string) => `http://localhost/app/reclamar/${token}`,
  friendsApi: {
    list: vi.fn().mockResolvedValue([{ playerId: 8, firstName: 'Luis', lastName: 'Paz', clubName: null, eloNational: 1700, platform: { bullet: null, blitz: null, rapid: null, classical: null }, since: '2026-01-01T00:00:00Z' }]),
    requests: vi.fn((d: string) => Promise.resolve(d === 'incoming'
      ? [{ requestId: 1, playerId: 10, firstName: 'Eva', lastName: 'Mora', eloNational: null, direction: 'INCOMING', createdAt: '2026-01-01T00:00:00Z' }]
      : [{ requestId: 2, playerId: 11, firstName: 'Ivo', lastName: 'Lara', eloNational: null, direction: 'OUTGOING', createdAt: '2026-01-01T00:00:00Z' }])),
    status: vi.fn().mockResolvedValue({ status: 'NONE', requestId: null }),
    request: vi.fn(), accept: vi.fn(), decline: vi.fn(), remove: vi.fn(),
  },
}));

vi.mock('./api/tournaments', () => ({
  tournamentsApi: {
    mine: vi.fn().mockResolvedValue({ organized: [{ id: 7, name: 'Copa Torre', status: 'OPEN' }], registered: [] }),
    registerAll: vi.fn().mockResolvedValue([{ playerId: 101, outcome: 'REGISTERED', message: null }, { playerId: 102, outcome: 'REGISTERED', message: null }]),
  },
}));

import { Landing } from './pages/Landing';
import { Dashboard } from './pages/Dashboard';
import { ProfileEdit } from './pages/ProfileEdit';
import { PlayerDetail, PlayerSearch } from './pages/Players';
import { Ranking } from './pages/Ranking';
import { PublicRanking } from './pages/PublicRanking';
import { Friends } from './pages/Friends';
import { Club } from './pages/Club';
import { ClaimInvite } from './pages/ClaimInvite';
import { claimApi, organizationsApi } from './api/users';
import { tournamentsApi } from './api/tournaments';
import { Layout } from './components/Layout';

const renderPage = (ui: React.ReactElement, path = '/app') => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter></QueryClientProvider>);
};

describe('páginas: render y accesibilidad', () => {
  it('Landing redirige si hay sesión (no renderiza el hero)', async () => {
    const { container } = renderPage(<Landing />, '/');
    expect(container.querySelector('.cq-hero')).toBeNull();
  });

  it('Dashboard muestra ficha, ratings y gráfico', async () => {
    const { container } = renderPage(<Layout><Dashboard /></Layout>);
    await screen.findByText(/Hola, Ana/);
    await screen.findByRole('img', { name: /de 1480 a 1500/ });
    expect(screen.getByText('1600')).toBeInTheDocument();
    await screen.findByText('¿Eres tú?');
    expect(screen.getByRole('button', { name: 'Soy yo' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Vincular mi ficha' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('Perfil carga el formulario con los datos actuales', async () => {
    const { container } = renderPage(<ProfileEdit />);
    await waitFor(() => expect(screen.getByLabelText('Nombre')).toHaveValue('Ana'));
    expect(await axe(container)).toHaveNoViolations();
  });

  it('Jugadores y ranking listan resultados', async () => {
    const search = renderPage(<PlayerSearch />);
    expect(await axe(search.container)).toHaveNoViolations();
    search.unmount();
    const ranking = renderPage(<Ranking />);
    await ranking.findByText('Luis Paz');
    expect(await axe(ranking.container)).toHaveNoViolations();
    ranking.unmount();
    const pub = renderPage(<PublicRanking />, '/ranking');
    await pub.findByText('Vicente M.');
    expect(pub.getByText('1923')).toBeInTheDocument();
    expect(pub.queryByRole('link', { name: 'Vicente M.' })).toBeNull(); // la vista pública no enlaza perfiles
    expect(await axe(pub.container)).toHaveNoViolations();
    pub.unmount();
    const detail = renderPage(<PlayerDetail />, '/app/jugadores/7');
    await detail.findByText('Agregar amigo');
    expect(detail.queryByText('ana@x.cl')).toBeNull();
    expect(await axe(detail.container)).toHaveNoViolations();
  });

  it('Amigos muestra lista y solicitudes', async () => {
    const { container, findByText } = renderPage(<Friends />);
    await findByText('Eva Mora');
    await findByText('Ivo Lara');
    await findByText(/Mis amigos \(1\)/);
    expect(await axe(container)).toHaveNoViolations();
  });

  it('Club muestra el panel del organizador con su roster', async () => {
    const { container, findByText } = renderPage(<Club />, '/club');
    await findByText('Pedro Rojas');
    await findByText(/Plan FREE/);
    expect(screen.getByRole('link', { name: 'Descargar plantilla' })).toHaveAttribute('download', 'plantilla-roster.csv');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('Club: carga masiva en el servidor e inscripción de los importados en un torneo abierto', async () => {
    renderPage(<Club />, '/club');
    await screen.findByText('Pedro Rojas');
    const csv = 'nombre,apellido,email,rut,elo\nBeto,Uno,,,1500\nCarla,Dos,,,1400\nDino,Tres,dino@x.cl,,\n';
    const file = new File([csv], 'roster.csv', { type: 'text/csv' });
    fireEvent.change(screen.getByLabelText('Archivo CSV del roster'), { target: { files: [file] } });
    fireEvent.change(await screen.findByLabelText('Inscribir también en'), { target: { value: '7' } });
    fireEvent.click(screen.getByRole('button', { name: 'Importar' }));
    expect(await screen.findByText('Importados 2, 1 duplicado, inscritos en Copa Torre: 2')).toBeInTheDocument();
    expect(vi.mocked(organizationsApi.importRoster).mock.calls[0][0]).toHaveLength(3);
    expect(tournamentsApi.registerAll).toHaveBeenCalledWith(7, [101, 102]);
  });

  it('Club: invitación con enlace y QR para que el jugador reclame su perfil', async () => {
    const { container } = renderPage(<Club />, '/club');
    fireEvent.click(await screen.findByRole('button', { name: 'Invitar a Pedro Rojas a reclamar su perfil' }));
    expect(await screen.findByLabelText('Enlace de invitación de Pedro Rojas')).toHaveValue('http://localhost/app/reclamar/tokPedro');
    expect(await screen.findByRole('img', { name: 'QR de invitación de Pedro Rojas' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('Reclamar: el jugador ve de qué perfil se trata y lo une a su cuenta', async () => {
    const { container } = renderPage(<Routes><Route path="/app/reclamar/:token" element={<ClaimInvite />} /></Routes>, '/app/reclamar/tokPedro');
    expect(await screen.findByRole('heading', { name: '¿Eres Pedro R.?' })).toBeInTheDocument();
    expect(screen.getByText(/Club Torre te cargó en su roster/)).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getByRole('button', { name: 'Sí, soy yo: unirlo a mi cuenta' }));
    expect(await screen.findByText(/el perfil quedó unido a tu cuenta/)).toBeInTheDocument();
    expect(claimApi.claim).toHaveBeenCalledWith('tokPedro');
  });
});
