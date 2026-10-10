import { act, fireEvent, render, screen } from '@testing-library/react';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as axeMatchers from 'vitest-axe/matchers';
import { TOAST_MS, ToastProvider, useToast } from '@chessquery/ui-lib';

expect.extend(axeMatchers);

/**
 * Experiencia transversal: avisos emergentes (Toast), manejo de la sesión (401 → renovar o volver a entrar, aviso al
 * cerrar sesión) y menús por modo (jugador / organizador).
 */
const auth = vi.hoisted(() => ({ signinSilent: vi.fn(), signinRedirect: vi.fn() }));
vi.mock('react-oidc-context', () => ({ useAuth: () => auth }));

import { http, setUnauthorizedHandler } from './api/client';
import { SessionManager } from './auth/session';
import { SIGNED_OUT_FLAG } from './auth/provider';
import { isActive, menuFor, organizerMode } from './components/Layout';

const Demo = () => {
  const toast = useToast();
  return (
    <>
      <button type="button" onClick={() => toast.success('Perfil guardado')}>ok</button>
      <button type="button" onClick={() => toast.error('No se pudo guardar')}>mal</button>
    </>
  );
};

describe('avisos emergentes', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('éxito como status, error como alert, se cierran solos y con el botón; accesibles', async () => {
    const { container } = render(<ToastProvider><Demo /></ToastProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'ok' }));
    fireEvent.click(screen.getByRole('button', { name: 'mal' }));
    expect(screen.getByRole('status')).toHaveTextContent('Perfil guardado');
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo guardar');
    vi.useRealTimers();
    expect(await axe(container)).toHaveNoViolations();
    vi.useFakeTimers();
    fireEvent.click(screen.getAllByRole('button', { name: 'Cerrar aviso' })[1]);
    expect(screen.queryByRole('alert')).toBeNull();
    act(() => { vi.advanceTimersByTime(TOAST_MS + 10); });
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('pasar el mouse pausa el cierre automático y como máximo hay 3 a la vez', () => {
    render(<ToastProvider><Demo /></ToastProvider>);
    for (let i = 0; i < 4; i++) fireEvent.click(screen.getByRole('button', { name: 'ok' }));
    expect(screen.getAllByRole('status')).toHaveLength(3);
    fireEvent.mouseEnter(screen.getAllByRole('status')[0]);
    act(() => { vi.advanceTimersByTime(TOAST_MS * 2); });
    expect(screen.getAllByRole('status')).toHaveLength(1);
  });
});

/** Adaptador falso de axios: responde 401 a los tokens viejos y 200 al token renovado. */
const fakeAdapter = (calls: string[]) => async (config: InternalAxiosRequestConfig) => {
  const token = String(config.headers.Authorization ?? '');
  calls.push(token);
  if (token === 'Bearer nuevo') return { data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config };
  const response = { data: { status: 401, error: 'UNAUTHORIZED', message: 'Token vencido', timestamp: '' }, status: 401,
    statusText: 'Unauthorized', headers: new AxiosHeaders(), config };
  throw new AxiosError('401', 'ERR_BAD_REQUEST', config, null, response);
};

describe('sesión', () => {
  const calls: string[] = [];
  beforeEach(() => {
    calls.length = 0;
    http.defaults.adapter = fakeAdapter(calls);
    auth.signinSilent.mockReset();
    auth.signinRedirect.mockReset();
  });
  afterEach(() => { setUnauthorizedHandler(null); vi.useRealTimers(); });

  it('ante un 401 renueva una sola vez y repite el pedido con el token nuevo', async () => {
    setUnauthorizedHandler(async () => 'nuevo');
    const r = await http.get('/api/users/me', { headers: { Authorization: 'Bearer viejo' } });
    expect(r.data).toEqual({ ok: true });
    expect(calls).toEqual(['Bearer viejo', 'Bearer nuevo']);
  });

  it('si no se puede renovar, el error llega a la pantalla con su formato (sin bucles)', async () => {
    const handler = vi.fn().mockResolvedValue(null);
    setUnauthorizedHandler(handler);
    await expect(http.get('/api/users/me', { headers: { Authorization: 'Bearer viejo' } })).rejects.toMatchObject({ status: 401 });
    expect(handler).toHaveBeenCalledTimes(1);
    expect(calls).toEqual(['Bearer viejo']);
  });

  it('sesión vencida: avisa y vuelve al login de la misma página; cerrar sesión deja un aviso al volver', async () => {
    vi.useFakeTimers();
    auth.signinSilent.mockRejectedValue(new Error('login_required'));
    window.history.pushState({}, '', '/club/torneos/5?x=1');
    render(<ToastProvider><SessionManager><p>app</p></SessionManager></ToastProvider>);
    // Tres pedidos vencidos a la vez: una sola renovación, un solo aviso y una sola vuelta al login
    const failed = ['/a', '/b', '/c'].map((url) => http.get(url, { headers: { Authorization: 'Bearer viejo' } }).catch((e) => e));
    await vi.waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Tu sesión expiró'));
    expect(await Promise.all(failed)).toEqual([expect.objectContaining({ status: 401 }), expect.objectContaining({ status: 401 }),
      expect.objectContaining({ status: 401 })]);
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(auth.signinSilent).toHaveBeenCalledTimes(1);
    act(() => { vi.advanceTimersByTime(1600); });
    expect(auth.signinRedirect).toHaveBeenCalledTimes(1);
    expect(auth.signinRedirect).toHaveBeenCalledWith({ state: '/club/torneos/5?x=1' });

    sessionStorage.setItem(SIGNED_OUT_FLAG, '1');
    render(<ToastProvider><SessionManager><p>portada</p></SessionManager></ToastProvider>);
    expect(screen.getByRole('status')).toHaveTextContent('Cerraste sesión');
    expect(sessionStorage.getItem(SIGNED_OUT_FLAG)).toBeNull();
  });
});

describe('menús por modo', () => {
  it('el modo sale de la ruta y cada modo tiene su menú', () => {
    expect(organizerMode('/club')).toBe(true);
    expect(organizerMode('/club/torneos/5')).toBe(true);
    expect(organizerMode('/clubes')).toBe(false);
    expect(organizerMode('/app/torneos')).toBe(false);
    expect(menuFor(true, true).map(([, label]) => label)).toEqual(['Mi club', 'Torneos del club', 'Salas de juego']);
    expect(menuFor(true, false).map(([, label]) => label)).not.toContain('Crear mi club');
    expect(menuFor(false, false).at(-1)?.[1]).toBe('Crear mi club');
  });

  it('las raíces se marcan solo en su ruta exacta; el resto también en sus subrutas', () => {
    expect(isActive('/app', '/app')).toBe(true);
    expect(isActive('/app/torneos', '/app')).toBe(false);
    expect(isActive('/club/torneos', '/club')).toBe(false);
    expect(isActive('/club/torneos/5', '/club/torneos')).toBe(true);
    expect(isActive('/app/torneosx', '/app/torneos')).toBe(false);
  });
});
