import { http } from './client';
import type { ChallengeRequest, GameView, MyGames, OpenChallengeRequest, OpenChallengeView } from './gameTypes';

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

/** Desafíos abiertos: un enlace que acepta el primero que entre (para jugar con alguien que no es tu amigo). */
export const openChallengesApi = {
  create: (body: OpenChallengeRequest) => http.post<OpenChallengeView>('/api/games/open', body).then((r) => r.data),
  mine: () => http.get<OpenChallengeView[]>('/api/games/open').then((r) => r.data),
  get: (token: string) => http.get<OpenChallengeView>(`/api/games/open/${token}`).then((r) => r.data),
  accept: (token: string) => http.post<GameView>(`/api/games/open/${token}/accept`).then((r) => r.data),
  cancel: (token: string) => http.delete(`/api/games/open/${token}`).then(() => undefined),
};

/** Enlace del desafío abierto (lo abre el QR). */
export const openChallengeUrl = (token: string) => `${window.location.origin}/app/desafio/${token}`;

/** URL pública del PGN (descarga directa, sin login). */
export const pgnUrl = (id: number) => `${import.meta.env.VITE_API_URL || ''}/api/public/games/${id}/pgn`;
