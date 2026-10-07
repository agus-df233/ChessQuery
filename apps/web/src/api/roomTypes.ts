/** Contratos de las salas de juego (espejo de RoomDtos.java, servicio game). */
import type { GameView } from './gameTypes';
import type { TimeControlCategory } from '../lib/timeControl';

export type RoomStatus = 'OPEN' | 'CLOSED';

export interface RoomRequest { name: string; boards: number; maxPlayers?: number; minutes: number; incrementSeconds: number }
export interface AssignRequest { whitePlayerId: number | null; blackPlayerId: number | null }

export interface Seat { playerId: number; name: string }
/** Un miembro y su puesto: `boardNo` null = espectador. */
export interface RoomMember { playerId: number; name: string; boardNo: number | null; color: 'WHITE' | 'BLACK' | null }
export interface RoomBoard { boardNo: number; white: Seat | null; black: Seat | null; game: GameView | null }

export interface RoomView {
  id: number; name: string; code: string; status: RoomStatus; boardCount: number; maxPlayers: number;
  initialSeconds: number; incrementSeconds: number; category: TimeControlCategory; version: number;
  organizer: boolean; myBoard: number | null; myGameId: number | null;
  members: RoomMember[]; boards: RoomBoard[];
}

export interface RoomSummary {
  id: number; name: string; code: string | null; status: RoomStatus; boardCount: number; maxPlayers: number;
  memberCount: number; category: TimeControlCategory; createdAt: string;
}
export interface MyRooms { organized: RoomSummary[]; joined: RoomSummary[] }
