import { act, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import type { GameView } from './api/gameTypes';

const game = { id: 9, status: 'ACTIVE', version: 3 } as GameView;

vi.mock('./api/games', () => ({
  gamesApi: {
    get: vi.fn(() => Promise.resolve(game)),
    // El servidor responde sin cambios (misma versión), como a los 25 s de espera
    waitChange: vi.fn(() => Promise.resolve(game)),
  },
}));

import { gamesApi } from './api/games';
import { useLiveGame } from './components/game/useLiveGame';

describe('useLiveGame', () => {
  it('encadena una nueva espera aunque la anterior vuelva sin cambios', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { unmount } = renderHook(() => useLiveGame(9), {
      wrapper: ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>,
    });
    await vi.waitFor(() => expect(vi.mocked(gamesApi.waitChange).mock.calls.length).toBeGreaterThanOrEqual(3));
    expect(gamesApi.waitChange).toHaveBeenCalledWith(9, 3, expect.anything());
    unmount();
  });

  it('no espera cambios de una partida terminada', async () => {
    vi.mocked(gamesApi.waitChange).mockClear();
    vi.mocked(gamesApi.get).mockResolvedValueOnce({ ...game, id: 10, status: 'FINISHED' });
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useLiveGame(10), {
      wrapper: ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>,
    });
    await vi.waitFor(() => expect(result.current.data?.status).toBe('FINISHED'));
    expect(gamesApi.waitChange).not.toHaveBeenCalled();
  });
});

/** WebSocket falso: guarda lo enviado y permite simular apertura, mensajes y cierre. */
class FakeSocket {
  static last: FakeSocket | undefined;
  sent: string[] = [];
  onopen?: () => void;
  onmessage?: (e: { data: string }) => void;
  onclose?: () => void;
  constructor(public url: string) { FakeSocket.last = this; }
  send(data: string) { this.sent.push(data); }
  close() { this.onclose?.(); }
}

describe('useLiveGame con WebSocket', () => {
  it('se suscribe, aplica solo versiones nuevas y vuelve al long polling si el socket cae', async () => {
    const { tokenStore } = await import('./auth/tokenStore');
    tokenStore.set('tok');
    vi.stubEnv('VITE_WS_URL', 'ws://localhost/ws');
    vi.stubGlobal('WebSocket', FakeSocket);
    vi.mocked(gamesApi.waitChange).mockClear();
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const { result } = renderHook(() => useLiveGame(9), {
      wrapper: ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>,
    });
    await vi.waitFor(() => expect(FakeSocket.last?.url).toBe('ws://localhost/ws?token=tok'));
    const socket = FakeSocket.last!;
    act(() => socket.onopen?.());
    expect(socket.sent).toContain(JSON.stringify({ action: 'subscribe', gameId: 9 }));

    act(() => socket.onmessage?.({ data: JSON.stringify({ type: 'game', game: { ...game, version: 5, ply: 1 } }) }));
    await vi.waitFor(() => expect(result.current.data?.version).toBe(5));
    act(() => socket.onmessage?.({ data: JSON.stringify({ type: 'game', game: { ...game, version: 4 } }) }));
    expect(result.current.data?.version).toBe(5); // una versión vieja no pisa la nueva
    act(() => socket.onmessage?.({ data: JSON.stringify({ type: 'game', game: { ...game, id: 99, version: 50 } }) }));
    expect(result.current.data?.version).toBe(5); // otra partida se ignora

    const pollsBefore = vi.mocked(gamesApi.waitChange).mock.calls.length;
    act(() => socket.onclose?.());
    await vi.waitFor(() => expect(vi.mocked(gamesApi.waitChange).mock.calls.length).toBeGreaterThan(pollsBefore));
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });
});
