/** Contratos del servicio game (espejo de GameDtos.java). */
import type { TimeControlCategory } from '../lib/timeControl';

export type GameStatus = 'PENDING' | 'ACTIVE' | 'FINISHED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED';
export type Outcome = 'WHITE_WINS' | 'BLACK_WINS' | 'DRAW';
export type ColorChoice = 'WHITE' | 'BLACK' | 'RANDOM';

export interface GameSide { playerId: number; name: string; ratingBefore: number | null; ratingAfter: number | null; clockMs: number }

export interface GameView {
  id: number; status: GameStatus; white: GameSide; black: GameSide; challengerId: number;
  initialSeconds: number; incrementSeconds: number; category: TimeControlCategory; rated: boolean; fen: string; moves: string[]; san: string[];
  ply: number; sideToMove: 'WHITE' | 'BLACK'; drawOfferBy: number | null; result: Outcome | null;
  termination: string | null; terminationLabel: string | null; version: number; createdAt: string; finishedAt: string | null;
  /** Partida de una sala de juego (null = desafío entre jugadores). */
  roomId?: number | null; boardNo?: number | null;
}

export interface MyGames { incoming: GameView[]; outgoing: GameView[]; active: GameView[]; finished: GameView[] }

export interface ChallengeRequest { opponentId: number; minutes: number; incrementSeconds: number; color: ColorChoice; rated: boolean }
