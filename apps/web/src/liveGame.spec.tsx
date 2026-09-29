import { renderHook } from '@testing-library/react';
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
