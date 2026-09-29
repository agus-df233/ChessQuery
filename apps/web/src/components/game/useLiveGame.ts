import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { gamesApi } from '../../api/games';
import type { GameView } from '../../api/gameTypes';

const RETRY_AFTER_ERROR_MS = 3_000;

/**
 * Partida en vivo: carga el estado y luego encadena long polls ({@code afterVersion}) mientras siga pendiente o en
 * juego. Cada respuesta actualiza la caché de React Query, así que las acciones propias (jugar, tablas) y las del
 * rival se ven igual. Si la red falla, reintenta a los 3 s. Para cambiar a WebSocket/AppSync basta con este hook.
 */
export const useLiveGame = (id: number) => {
  const qc = useQueryClient();
  const game = useQuery({ queryKey: ['game', id], queryFn: () => gamesApi.get(id) });
  const version = game.data?.version;
  const live = game.data && (game.data.status === 'PENDING' || game.data.status === 'ACTIVE');

  useEffect(() => {
    if (!live || version === undefined) return undefined;
    const controller = new AbortController();
    gamesApi.waitChange(id, version, controller.signal)
      .then((next) => qc.setQueryData<GameView>(['game', id], (prev) => (prev && prev.version > next.version ? prev : next)))
      .catch(() => {
        if (!controller.signal.aborted) setTimeout(() => void qc.invalidateQueries({ queryKey: ['game', id] }), RETRY_AFTER_ERROR_MS);
      });
    return () => controller.abort();
  }, [id, version, live, qc]);

  return game;
};
