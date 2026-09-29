import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { gamesApi } from '../../api/games';
import type { GameView } from '../../api/gameTypes';

const RETRY_AFTER_ERROR_MS = 3_000;

/**
 * Partida en vivo: carga el estado y luego encadena long polls ({@code afterVersion}) mientras siga pendiente o en
 * juego. Cada respuesta actualiza la caché de React Query, así que las acciones propias (jugar, tablas) y las del
 * rival se ven igual. Si la red falla, reintenta a los 3 s. Para cambiar a WebSocket/AppSync basta con este hook.
 *
 * {@code round} cuenta las esperas terminadas: si el servidor responde a los 25 s sin cambios (misma versión),
 * igual hay que lanzar la siguiente espera, y la versión sola no cambiaría las dependencias del efecto.
 */
export const useLiveGame = (id: number) => {
  const qc = useQueryClient();
  const [round, setRound] = useState(0);
  const game = useQuery({ queryKey: ['game', id], queryFn: () => gamesApi.get(id) });
  const version = game.data?.version;
  const live = game.data && (game.data.status === 'PENDING' || game.data.status === 'ACTIVE');

  useEffect(() => {
    if (!live || version === undefined) return undefined;
    const controller = new AbortController();
    let retry: ReturnType<typeof setTimeout> | undefined;
    const next = () => setRound((r) => r + 1);
    gamesApi.waitChange(id, version, controller.signal)
      .then((latest) => {
        qc.setQueryData<GameView>(['game', id], (prev) => (prev && prev.version > latest.version ? prev : latest));
        next();
      })
      .catch(() => {
        if (!controller.signal.aborted) retry = setTimeout(next, RETRY_AFTER_ERROR_MS);
      });
    return () => { controller.abort(); clearTimeout(retry); };
  }, [id, version, live, qc, round]);

  return game;
};
