import { useCallback, useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { gamesApi } from '../../api/games';
import type { GameView } from '../../api/gameTypes';
import { tokenStore } from '../../auth/tokenStore';

const RETRY_AFTER_ERROR_MS = 3_000;
const PING_EVERY_MS = 5 * 60_000; // API Gateway corta las conexiones con 10 min sin tráfico
const MAX_BACKOFF_MS = 30_000;

type OnGame = (game: GameView) => void;

/**
 * Abre el WebSocket de la partida (`<VITE_WS_URL>?token=…`), se suscribe y entrega cada estado nuevo. Si la conexión
 * cae, reintenta con espera creciente (1 s, 2 s, 4 s… máx. 30 s). `onUp` avisa si el socket está arriba, para apagar
 * o encender el long polling de respaldo. Devuelve la función que cierra todo.
 */
export function openLiveSocket(url: string, gameId: number, onGame: OnGame, onUp: (up: boolean) => void): () => void {
  let socket: WebSocket | undefined;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let ping: ReturnType<typeof setInterval> | undefined;
  let attempt = 0;
  let stopped = false;

  const connect = () => {
    const token = tokenStore.get();
    if (!token || stopped) return;
    socket = new WebSocket(`${url}?token=${encodeURIComponent(token)}`);
    socket.onopen = () => {
      attempt = 0;
      onUp(true);
      socket?.send(JSON.stringify({ action: 'subscribe', gameId }));
      ping = setInterval(() => socket?.send(JSON.stringify({ action: 'ping' })), PING_EVERY_MS);
    };
    socket.onmessage = (event) => {
      const msg = JSON.parse(String(event.data)) as { type?: string; game?: GameView };
      if (msg.type === 'game' && msg.game?.id === gameId) onGame(msg.game);
    };
    socket.onclose = () => {
      onUp(false);
      clearInterval(ping);
      if (!stopped) retry = setTimeout(connect, Math.min(MAX_BACKOFF_MS, 1000 * 2 ** attempt++));
    };
  };

  connect();
  return () => {
    stopped = true;
    clearTimeout(retry);
    clearInterval(ping);
    socket?.close();
  };
}

/**
 * Partida en vivo. Con `VITE_WS_URL`, las jugadas llegan por **WebSocket** (API Gateway WebSocket en la nube, `/ws` en
 * local). Mientras el socket no esté arriba (o si no hay `VITE_WS_URL`), encadena **long polls** (`afterVersion`):
 * el servidor responde apenas hay cambios o a los 25 s. Ambas vías actualizan la misma caché de React Query y solo
 * aceptan versiones más nuevas, así que las pantallas no saben cuál llegó primero.
 *
 * `round` cuenta las esperas terminadas: si el servidor responde sin cambios (misma versión), igual hay que lanzar la
 * siguiente espera, y la versión sola no cambiaría las dependencias del efecto.
 */
export const useLiveGame = (id: number) => {
  const qc = useQueryClient();
  const [round, setRound] = useState(0);
  const [socketUp, setSocketUp] = useState(false);
  const game = useQuery({ queryKey: ['game', id], queryFn: () => gamesApi.get(id) });
  const version = game.data?.version;
  const live = !!game.data && (game.data.status === 'PENDING' || game.data.status === 'ACTIVE');

  const apply = useCallback((latest: GameView) => {
    qc.setQueryData<GameView>(['game', id], (prev) => (prev && prev.version > latest.version ? prev : latest));
  }, [qc, id]);

  const wsUrl = import.meta.env.VITE_WS_URL as string | undefined;
  useEffect(() => {
    if (!wsUrl || !live) return undefined;
    const close = openLiveSocket(wsUrl, id, apply, setSocketUp);
    return () => { close(); setSocketUp(false); };
  }, [wsUrl, id, live, apply]);

  useEffect(() => {
    if (!live || version === undefined || socketUp) return undefined;
    const controller = new AbortController();
    let retry: ReturnType<typeof setTimeout> | undefined;
    const next = () => setRound((r) => r + 1);
    gamesApi.waitChange(id, version, controller.signal)
      .then((latest) => { apply(latest); next(); })
      .catch(() => {
        if (!controller.signal.aborted) retry = setTimeout(next, RETRY_AFTER_ERROR_MS);
      });
    return () => { controller.abort(); clearTimeout(retry); };
  }, [id, version, live, socketUp, apply, round]);

  return game;
};
