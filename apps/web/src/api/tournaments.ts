import { http } from './client';
import type {
  FederationTournament, GameResult, MyTournaments, RoundView, StandingView, TournamentDetail, TournamentRequest,
  TournamentStatus, TournamentView,
} from './tournamentTypes';

const base = (id: number) => `/api/tournaments/${id}`;

/** API autenticada de torneos: el organizador los dirige; el jugador se inscribe. */
export const tournamentsApi = {
  mine: () => http.get<MyTournaments>('/api/tournaments/mine').then((r) => r.data),
  create: (body: TournamentRequest) => http.post<TournamentDetail>('/api/tournaments', body).then((r) => r.data),
  update: (id: number, body: TournamentRequest) => http.put<TournamentDetail>(base(id), body).then((r) => r.data),
  register: (id: number, playerId: number) =>
    http.post<TournamentDetail>(`${base(id)}/registrations`, { playerId }).then((r) => r.data),
  unregister: (id: number, playerId: number) =>
    http.delete<TournamentDetail>(`${base(id)}/registrations/${playerId}`).then((r) => r.data),
  join: (id: number) => http.post<TournamentDetail>(`${base(id)}/join`).then((r) => r.data),
  leave: (id: number) => http.delete<TournamentDetail>(`${base(id)}/join`).then((r) => r.data),
  nextRound: (id: number) => http.post<RoundView>(`${base(id)}/rounds`).then((r) => r.data),
  setResult: (id: number, round: number, board: number, result: GameResult) =>
    http.put<RoundView>(`${base(id)}/rounds/${round}/boards/${board}`, { result }).then((r) => r.data),
  finish: (id: number) => http.post<StandingView[]>(`${base(id)}/finish`).then((r) => r.data),
  trf: (id: number) => http.get<string>(`${base(id)}/trf`, { responseType: 'text' }).then((r) => r.data),
};

/** Vista pública (sin login): se comparte por QR en la sala de juego. */
export const publicTournamentsApi = {
  list: (status?: TournamentStatus) =>
    http.get<TournamentView[]>('/api/public/tournaments', { params: { status } }).then((r) => r.data),
  detail: (id: number) => http.get<TournamentDetail>(`/api/public/tournaments/${id}`).then((r) => r.data),
  rounds: (id: number) => http.get<RoundView[]>(`/api/public/tournaments/${id}/rounds`).then((r) => r.data),
  standings: (id: number) => http.get<StandingView[]>(`/api/public/tournaments/${id}/standings`).then((r) => r.data),
  calendar: () => http.get<FederationTournament[]>('/api/public/tournaments/calendar').then((r) => r.data),
};
