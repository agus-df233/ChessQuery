/** Contratos del servicio tournament (espejo de TournamentDtos.java). */
import type { TimeControlCategory } from '../lib/timeControl';

export type TournamentFormat = 'SWISS' | 'ROUND_ROBIN';
export type TournamentStatus = 'OPEN' | 'IN_PROGRESS' | 'FINISHED';
export type GameResult = 'WHITE_WINS' | 'BLACK_WINS' | 'DRAW' | 'WHITE_FORFEIT_WIN' | 'BLACK_FORFEIT_WIN' | 'DOUBLE_FORFEIT' | 'BYE';

export interface TournamentView {
  id: number; name: string; city: string | null; region: string | null; startDate: string; endDate: string | null;
  format: TournamentFormat; roundsPlanned: number; currentRound: number; timeControl: string | null;
  baseMinutes: number | null; incrementSeconds: number | null; category: TimeControlCategory; rated: boolean;
  status: TournamentStatus; organizationId: number;
  /** Confirmados (los que juegan); pendientes de aprobación y en lista de espera. */
  playerCount: number; pendingCount: number; waitlistCount: number;
  /** Reglas de inscripción (null = sin límite). */
  registrationClosesAt: string | null; maxPlayers: number | null; requiresApproval: boolean;
  minRating: number | null; maxRating: number | null; checkinRequired: boolean;
}

export interface PlayerRef { playerId: number; name: string; title: string | null; rating: number | null }
/** `withdrawnFromRound`: null = juega; N = retirado desde la ronda N. */
export interface EntryView { startRank: number; player: PlayerRef; clubName: string | null; withdrawnFromRound: number | null }
export interface TournamentDetail { tournament: TournamentView; players: EntryView[] }
export interface BoardView { board: number; white: PlayerRef; black: PlayerRef | null; result: GameResult | null; resultLabel: string | null }
export interface RoundView { number: number; complete: boolean; boards: BoardView[] }
export interface StandingView {
  position: number; player: PlayerRef; points: number; buchholzCut1: number; buchholz: number;
  sonnebornBerger: number; wins: number; played: number;
}
export interface MyTournaments { organized: TournamentView[]; registered: TournamentView[] }

export interface TournamentRequest {
  name: string; city?: string; region?: string; startDate: string; endDate?: string;
  format: TournamentFormat; rounds: number; timeControl?: string; rated: boolean;
  /** Ritmo estructurado: define qué ELO ChessQuery actualiza el torneo. */
  baseMinutes?: number; incrementSeconds?: number;
  registrationClosesAt?: string | null; maxPlayers?: number | null; requiresApproval?: boolean;
  minRating?: number | null; maxRating?: number | null; checkinRequired?: boolean;
}

export type RegistrationStatus = 'PENDING' | 'CONFIRMED' | 'WAITLIST' | 'WITHDRAWN';

/** Una inscripción vista por el organizador o por el propio jugador (con el código de su QR de acreditación). */
export interface RegistrationView {
  playerId: number; name: string; title: string | null; clubName: string | null; seedRating: number;
  status: RegistrationStatus; checkedInAt: string | null; withdrawnFromRound: number | null; checkinCode: string;
  createdAt: string;
}
export interface CheckinResult { registration: RegistrationView; alreadyCheckedIn: boolean }
export interface BulkRow { playerId: number; outcome: 'REGISTERED' | 'ALREADY_REGISTERED' | 'ERROR'; message: string | null }

export interface FederationTournament {
  federationTournamentId: string; title: string; city: string | null; region: string | null; clubName: string | null;
  startDate: string; endDate: string | null; type: string | null; rounds: number | null; timeControl: string | null;
  category: string | null; ratedNational: boolean; ratedFide: boolean;
}
