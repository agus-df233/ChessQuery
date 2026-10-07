import type { GameView, Outcome } from '../../api/gameTypes';
import { CATEGORY_LABEL } from '../../lib/timeControl';

/** "Relámpago 3+2": el ritmo define qué ELO ChessQuery cuenta (ver lib/timeControl). */
export const timeControl = (g: GameView) => `${CATEGORY_LABEL[g.category]} ${g.initialSeconds / 60}+${g.incrementSeconds}`;

/** Resultado desde mi punto de vista ("Ganaste", "Perdiste", "Tablas") o neutro si no juego. */
export const resultFor = (result: Outcome | null, myId?: number, g?: GameView) => {
  if (!result) return '';
  if (result === 'DRAW') return 'Tablas';
  const whiteWon = result === 'WHITE_WINS';
  if (!g || myId === undefined || (g.white.playerId !== myId && g.black.playerId !== myId)) {
    return whiteWon ? 'Ganan blancas' : 'Ganan negras';
  }
  return whiteWon === (g.white.playerId === myId) ? 'Ganaste' : 'Perdiste';
};

/** Cambio de rating con signo ("+13", "−26"). */
export const ratingDelta = (before: number | null, after: number | null) => {
  if (before == null || after == null) return null;
  const d = after - before;
  return d >= 0 ? `+${d}` : `−${Math.abs(d)}`;
};

export const opponentOf = (g: GameView, myId: number) => (g.white.playerId === myId ? g.black : g.white);
