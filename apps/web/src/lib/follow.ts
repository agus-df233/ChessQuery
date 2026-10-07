import type { GameResult, RoundView, StandingView } from '../api/tournamentTypes';

/** Lo que vivió un jugador en una ronda, desde su punto de vista. */
export interface RoundLine {
  round: number;
  board: number | null;
  color: 'blancas' | 'negras' | null;
  rival: string | null;
  outcome: string;
}

/** Resultado desde el lado del jugador: "ganó", "perdió", "tablas", "ganó por no presentación", "bye", "en juego". */
export const outcomeFor = (result: GameResult | null, white: boolean): string => {
  if (result === null) return 'en juego';
  if (result === 'BYE') return 'descansa (bye)';
  if (result === 'DRAW') return 'tablas';
  if (result === 'DOUBLE_FORFEIT') return 'no se presentó';
  const whiteWon = result === 'WHITE_WINS' || result === 'WHITE_FORFEIT_WIN';
  const forfeit = result === 'WHITE_FORFEIT_WIN' || result === 'BLACK_FORFEIT_WIN';
  const won = whiteWon === white;
  if (forfeit) return won ? 'ganó por no presentación' : 'perdió por no presentación';
  return won ? 'ganó' : 'perdió';
};

/** Historia del jugador ronda a ronda (la más reciente primero). */
export const historyOf = (rounds: RoundView[], playerId: number): RoundLine[] => [...rounds].reverse().map((r) => {
  const b = r.boards.find((x) => x.white.playerId === playerId || x.black?.playerId === playerId);
  if (!b) return { round: r.number, board: null, color: null, rival: null, outcome: 'no juega esta ronda' };
  if (!b.black) return { round: r.number, board: b.board, color: null, rival: null, outcome: 'descansa (bye)' };
  const white = b.white.playerId === playerId;
  return { round: r.number, board: b.board, color: white ? 'blancas' : 'negras', rival: (white ? b.black : b.white).name,
    outcome: outcomeFor(b.result, white) };
});

/** Posición y puntos del jugador en la tabla (null si aún no hay tabla). */
export const standingOf = (rows: StandingView[], playerId: number) => rows.find((r) => r.player.playerId === playerId) ?? null;

/** El jugador que sigo en este torneo se recuerda en el navegador (nada sale del dispositivo). */
const key = (tournamentId: number) => `cq-follow-${tournamentId}`;
export const followed = (tournamentId: number): number | null => {
  try { const v = localStorage.getItem(key(tournamentId)); return v ? Number(v) : null; } catch { return null; }
};
export const follow = (tournamentId: number, playerId: number | null) => {
  try {
    if (playerId === null) localStorage.removeItem(key(tournamentId));
    else localStorage.setItem(key(tournamentId), String(playerId));
  } catch { /* sin almacenamiento (modo privado): se sigue solo mientras la página esté abierta */ }
};
