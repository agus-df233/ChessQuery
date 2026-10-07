import { http } from './client';
import type { AssignRequest, MyRooms, RoomRequest, RoomView } from './roomTypes';

/** Espera máxima del servidor (25 s) + margen de red, igual que en las partidas. */
const LONG_POLL_TIMEOUT_MS = 35_000;

const data = <T,>(p: Promise<{ data: T }>) => p.then((r) => r.data);

/** Salas de juego: el organizador las crea y administra; los jugadores entran con el código. */
export const roomsApi = {
  mine: () => data(http.get<MyRooms>('/api/rooms/mine')),
  create: (body: RoomRequest) => data(http.post<RoomView>('/api/rooms', body)),
  get: (id: number) => data(http.get<RoomView>(`/api/rooms/${id}`)),
  /** Long polling: vuelve cuando la sala pasa de `version` o a los 25 s con el estado actual. */
  waitChange: (id: number, version: number, signal?: AbortSignal) =>
    data(http.get<RoomView>(`/api/rooms/${id}`, { params: { afterVersion: version }, timeout: LONG_POLL_TIMEOUT_MS, signal })),
  update: (id: number, body: RoomRequest) => data(http.put<RoomView>(`/api/rooms/${id}`, body)),
  join: (code: string) => data(http.post<RoomView>('/api/rooms/join', { code })),
  leave: (id: number, playerId: number) => http.delete(`/api/rooms/${id}/members/${playerId}`).then(() => undefined),
  remove: (id: number, playerId: number) => data(http.delete<RoomView>(`/api/rooms/${id}/members/${playerId}`)),
  assign: (id: number, boardNo: number, body: AssignRequest) => data(http.put<RoomView>(`/api/rooms/${id}/boards/${boardNo}`, body)),
  start: (id: number, boardNo: number) => data(http.post<RoomView>(`/api/rooms/${id}/boards/${boardNo}/start`)),
  startAll: (id: number) => data(http.post<RoomView>(`/api/rooms/${id}/start`)),
  rematch: (id: number, boardNo: number) => data(http.post<RoomView>(`/api/rooms/${id}/boards/${boardNo}/rematch`)),
  close: (id: number) => data(http.post<RoomView>(`/api/rooms/${id}/close`)),
};

/** Enlace para entrar a una sala (lo abre el QR que proyecta el profesor). */
export const joinUrl = (code: string) => `${window.location.origin}/app/salas?codigo=${code}`;
