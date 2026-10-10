import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as axeMatchers from 'vitest-axe/matchers';

expect.extend(axeMatchers);

/** Portada sin sesión: accesos de jugador y organizador con Google, correo (Entra/simulado) y errores del IdP. */
const auth = vi.hoisted(() => ({
  isAuthenticated: false, isLoading: false, error: undefined as Error | undefined, signinRedirect: vi.fn(),
}));
vi.mock('react-oidc-context', () => ({ useAuth: () => auth }));

import { Landing } from './pages/Landing';

const renderLanding = () => render(<MemoryRouter><Landing /></MemoryRouter>);

describe('Portada', () => {
  beforeEach(() => { auth.error = undefined; auth.signinRedirect.mockClear(); });

  it('muestra los dos accesos y cada uno entra con Google y vuelve a su sección', async () => {
    const { container } = renderLanding();
    expect(screen.getByRole('heading', { name: /Juega y sigue tu progreso/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Tu club y tus torneos/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Entrar como jugador con Google' }));
    expect(auth.signinRedirect).toHaveBeenLastCalledWith({ extraQueryParams: { domain_hint: 'google' }, state: '/app' });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar como organizador con Google' }));
    expect(auth.signinRedirect).toHaveBeenLastCalledWith({ extraQueryParams: { domain_hint: 'google' }, state: '/club' });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar con mi correo' }));
    expect(auth.signinRedirect).toHaveBeenLastCalledWith();
    expect(screen.getByText(/no guarda contraseñas/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ranking de Chile' })).toHaveAttribute('href', '/ranking');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('con Cognito va directo a Google y no ofrece el correo (el login es solo con Google)', () => {
    vi.stubEnv('VITE_OIDC_PROVIDER', 'cognito');
    renderLanding();
    fireEvent.click(screen.getByRole('button', { name: 'Entrar como jugador con Google' }));
    expect(auth.signinRedirect).toHaveBeenCalledWith({ extraQueryParams: { identity_provider: 'Google' }, state: '/app' });
    expect(screen.queryByRole('button', { name: 'Entrar con mi correo' })).toBeNull();
    vi.unstubAllEnvs();
  });

  it('explica cuando el usuario cancela en Google', () => {
    auth.error = new Error('access_denied: the user canceled');
    renderLanding();
    expect(screen.getByRole('alert')).toHaveTextContent('Cancelaste el inicio de sesión');
  });

  it('mensaje genérico ante otros errores del IdP', () => {
    auth.error = new Error('server_error');
    renderLanding();
    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos iniciar tu sesión');
  });
});
