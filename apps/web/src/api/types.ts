/** Contratos del servicio users (espejo de los records Java). JSON en camelCase. */

export interface Ratings {
  national: number | null; fideStandard: number | null; fideRapid: number | null; fideBlitz: number | null;
  platformBullet: number | null; platformBlitz: number | null; platformRapid: number | null; platformClassical: number | null;
  lichessBullet: number | null; lichessBlitz: number | null; lichessRapid: number | null; lichessClassical: number | null;
  chesscomBullet: number | null; chesscomBlitz: number | null; chesscomRapid: number | null; chesscomDaily: number | null;
}

/** ELO ChessQuery por ritmo (null = aún no juega ese ritmo por rating). */
export interface PlatformRatings { bullet: number | null; blitz: number | null; rapid: number | null; classical: number | null }

export interface Country { id: number; isoCode: string; name: string; fideFederation: string | null }
export interface Club { id: number; name: string; city: string | null; federationCode: string | null }

export interface Profile {
  id: number; firstName: string; lastName: string; displayName: string | null;
  email: string | null; rut: string | null; birthDate: string | null; gender: string | null;
  region: string | null; country: Country | null; club: Club | null;
  fideId: string | null; federationId: string | null;
  lichessUsername: string | null; chesscomUsername: string | null;
  ratings: Ratings; currentTitle: string | null; ageCategory: string;
  enrichmentSource: string | null; enrichedAt: string | null;
  provisional: boolean; createdByOrganizerId: number | null; active: boolean; tags: string[];
  createdAt: string; updatedAt: string;
}

/** Lo que cualquier jugador ve de otro: sin RUT, email, fecha de nacimiento ni género. */
export interface PublicProfile {
  id: number; firstName: string; lastName: string; displayName: string | null; currentTitle: string | null;
  region: string | null; country: Country | null; club: Club | null; ageCategory: string;
  fideId: string | null; federationId: string | null; lichessUsername: string | null; chesscomUsername: string | null;
  ratings: Ratings; createdAt: string;
}

export interface Me { profile: Profile; organizationId: number | null; organizer: boolean; roles: string[] }

export interface SearchResult {
  id: number; firstName: string; lastName: string; currentTitle: string | null; clubName: string | null;
  countryIso: string | null; fideId: string | null; eloNational: number | null; eloFideStandard: number | null;
  platform: PlatformRatings;
}

/** Tipos por los que se puede rankear: nacional, FIDE y ELO ChessQuery por ritmo. */
export type RankedType = 'NATIONAL' | 'FIDE_STANDARD' | 'FIDE_RAPID' | 'FIDE_BLITZ'
  | 'PLATFORM_BULLET' | 'PLATFORM_BLITZ' | 'PLATFORM_RAPID' | 'PLATFORM_CLASSICAL';

export interface RankingEntry {
  position: number; playerId: number; firstName: string; lastName: string; currentTitle: string | null;
  region: string | null; clubName: string | null; ratingType: RankedType; rating: number | null;
  eloNational: number | null; eloFideStandard: number | null; ageCategory: string;
}

export interface RatingPoint { recordedAt: string; rating: number; previous: number | null; delta: number | null; source: string | null }

export type RatingType =
  | 'NATIONAL' | 'FIDE_STANDARD' | 'FIDE_RAPID' | 'FIDE_BLITZ'
  | 'PLATFORM_BULLET' | 'PLATFORM_BLITZ' | 'PLATFORM_RAPID' | 'PLATFORM_CLASSICAL'
  | 'LICHESS_BULLET' | 'LICHESS_BLITZ' | 'LICHESS_RAPID' | 'LICHESS_CLASSICAL'
  | 'CHESSCOM_BULLET' | 'CHESSCOM_BLITZ' | 'CHESSCOM_RAPID' | 'CHESSCOM_DAILY';

export interface UpdateProfileRequest {
  firstName?: string; lastName?: string; displayName?: string; rut?: string; birthDate?: string; gender?: string;
  countryId?: number; clubId?: number; region?: string; lichessUsername?: string; chesscomUsername?: string;
}

export interface Organization {
  id: number; name: string; city: string | null; description: string | null; logoUrl: string | null;
  plan: 'FREE' | 'PRO'; rosterCount: number; maxRosterPlayers: number; maxActiveTournaments: number;
}
export interface OrganizationRequest { name: string; city?: string; description?: string; logoUrl?: string }

export interface RosterCreateRequest {
  firstName: string; lastName: string; rut?: string; email?: string; eloNational?: number; eloFideStandard?: number;
  clubId?: number; tags?: string[];
}

export interface Friend {
  playerId: number; firstName: string; lastName: string; clubName: string | null;
  eloNational: number | null; platform: PlatformRatings; since: string;
}
export interface FriendRequest {
  requestId: number; playerId: number; firstName: string; lastName: string; eloNational: number | null;
  direction: 'INCOMING' | 'OUTGOING'; createdAt: string;
}
export type FriendshipState = 'NONE' | 'PENDING_OUT' | 'PENDING_IN' | 'FRIENDS';
export interface FriendshipStatus { status: FriendshipState; requestId: number | null }

/** Cuerpo de error único de la plataforma. */
export interface ApiError { status: number; error: string; message: string; timestamp: string }

/** Reclamo de una ficha federada: por id de ficha ("¿eres tú?") o por id federativo, con RUT si la ficha lo tiene. */
export interface ClaimRequest { playerId?: number; federationId?: string; rut?: string }
