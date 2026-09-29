/** Contratos del servicio tournament (espejo de TournamentDtos.java). */

export type TournamentFormat = 'SWISS' | 'ROUND_ROBIN';
export type TournamentStatus = 'OPEN' | 'IN_PROGRESS' | 'FINISHED';
export type GameResult = 'WHITE_WINS' | 'BLACK_WINS' | 'DRAW' | 'WHITE_FORFEIT_WIN' | 'BLACK_FORFEIT_WIN' | 'DOUBLE_FORFEIT' | 'BYE';

export interface TournamentView {
  id: number; name: string; city: string | null; region: string | null; startDate: string; endDate: string | null;
  format: TournamentFormat; roundsPlanned: number; currentRound: number; timeControl: string | null; rated: boolean;
  status: TournamentStatus; organizationId: number; playerCount: number;
}

export interface PlayerRef { playerId: number; name: string; title: string | null; rating: number | null }
export interface EntryView { startRank: number; player: PlayerRef; clubName: string | null }
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
}

export interface FederationTournament {
  federationTournamentId: string; title: string; city: string | null; region: string | null; clubName: string | null;
  startDate: string; endDate: string | null; type: string | null; rounds: number | null; timeControl: string | null;
  category: string | null; ratedNational: boolean; ratedFide: boolean;
}
