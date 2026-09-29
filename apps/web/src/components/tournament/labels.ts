import type { GameResult, TournamentFormat, TournamentStatus, TournamentView } from '../../api/tournamentTypes';

export const STATUS_LABEL: Record<TournamentStatus, string> = {
  OPEN: 'Inscripciones abiertas', IN_PROGRESS: 'En juego', FINISHED: 'Terminado',
};
export const STATUS_BADGE: Record<TournamentStatus, 'success' | 'warning' | 'neutral'> = {
  OPEN: 'success', IN_PROGRESS: 'warning', FINISHED: 'neutral',
};
export const FORMAT_LABEL: Record<TournamentFormat, string> = { SWISS: 'Suizo', ROUND_ROBIN: 'Todos contra todos' };

/** Resultados que el organizador puede cargar en una mesa (el bye se asigna solo). */
export const RESULT_OPTIONS: [GameResult, string][] = [
  ['WHITE_WINS', '1-0'], ['DRAW', '½-½'], ['BLACK_WINS', '0-1'],
  ['WHITE_FORFEIT_WIN', '1-0 (no se presentó negras)'], ['BLACK_FORFEIT_WIN', '0-1 (no se presentó blancas)'],
  ['DOUBLE_FORFEIT', '0-0 (ninguno se presentó)'],
];

export const formatDate = (iso: string) => new Date(`${iso}T12:00:00`).toLocaleDateString('es-CL', { day: 'numeric', month: 'short', year: 'numeric' });

export const summary = (t: TournamentView) =>
  [formatDate(t.startDate), t.city, FORMAT_LABEL[t.format], `${t.roundsPlanned} rondas`, t.timeControl, `${t.playerCount} jugadores`]
    .filter(Boolean).join(' · ');
