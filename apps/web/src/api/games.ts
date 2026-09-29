import { http } from './client';
import type { ChallengeRequest, GameView, MyGames } from './gameTypes';

/** Espera máxima del servidor (25 s) + margen de red: el cliente no debe cortar antes que el servidor. */
const LONG_POLL_TIMEOUT_MS = 35_000;

const act = (id: number, action: string) => http.post<GameView>(`/api/games/${id}/${action}`).then((r) => r.data);

/** Partidas del jugador autenticado. */
export const gamesApi = {
  mine: () => http.get<MyGames>('/api/games/mine').then((r) => r.data),
  challenge: (body: ChallengeRequest) => http.post<GameView>('/api/games', body).then((r) => r.data),
  get: (id: number) => http.get<GameView>(`/api/games/${id}`).then((r) => r.data),
  /** Long polling: vuelve cuando la partida pasa de {@code version} o a los 25 s con el estado actual. */
  waitChange: (id: number, version: number, signal?: AbortSignal) =>
    http.get<GameView>(`/api/games/${id}`, { params: { afterVersion: version }, timeout: LONG_POLL_TIMEOUT_MS, signal })
        .then((r) => r.data),
  move: (id: number, uci: string) => http.post<GameView>(`/api/games/${id}/moves`, { uci }).then((r) => r.data),
  accept: (id: number) => act(id, 'accept'),
  decline: (id: number) => act(id, 'decline'),
  cancel: (id: number) => act(id, 'cancel'),
  resign: (id: number) => act(id, 'resign'),
  offerDraw: (id: number) => act(id, 'draw/offer'),
  acceptDraw: (id: number) => act(id, 'draw/accept'),
  declineDraw: (id: number) => act(id, 'draw/decline'),
};

/** URL pública del PGN (descarga directa, sin login). */
export const pgnUrl = (id: number) => `${import.meta.env.VITE_API_URL || ''}/api/public/games/${id}/pgn`;
