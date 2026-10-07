import type { RankedType, Ratings, RatingType } from '../api/types';

/** Modalidades agrupadas por fuente para pintar la grilla de ratings y el selector del gráfico. */
export const RATING_GROUPS: { source: string; items: { key: keyof Ratings; type: RatingType; label: string }[] }[] = [
  { source: 'Federado', items: [
    { key: 'national', type: 'NATIONAL', label: 'ELO Nacional' },
    { key: 'fideStandard', type: 'FIDE_STANDARD', label: 'FIDE Estándar' },
    { key: 'fideRapid', type: 'FIDE_RAPID', label: 'FIDE Rápidas' },
    { key: 'fideBlitz', type: 'FIDE_BLITZ', label: 'FIDE Blitz' },
  ]},
  { source: 'ChessQuery', items: [
    { key: 'platformBullet', type: 'PLATFORM_BULLET', label: 'Bala' },
    { key: 'platformBlitz', type: 'PLATFORM_BLITZ', label: 'Relámpago' },
    { key: 'platformRapid', type: 'PLATFORM_RAPID', label: 'Rápida' },
    { key: 'platformClassical', type: 'PLATFORM_CLASSICAL', label: 'Clásica' },
  ]},
  { source: 'Lichess', items: [
    { key: 'lichessBullet', type: 'LICHESS_BULLET', label: 'Bullet' },
    { key: 'lichessBlitz', type: 'LICHESS_BLITZ', label: 'Blitz' },
    { key: 'lichessRapid', type: 'LICHESS_RAPID', label: 'Rápidas' },
    { key: 'lichessClassical', type: 'LICHESS_CLASSICAL', label: 'Clásicas' },
  ]},
  { source: 'Chess.com', items: [
    { key: 'chesscomBullet', type: 'CHESSCOM_BULLET', label: 'Bullet' },
    { key: 'chesscomBlitz', type: 'CHESSCOM_BLITZ', label: 'Blitz' },
    { key: 'chesscomRapid', type: 'CHESSCOM_RAPID', label: 'Rápidas' },
    { key: 'chesscomDaily', type: 'CHESSCOM_DAILY', label: 'Diarias' },
  ]},
];

/** Nombre completo con título por delante ("FM Ana Soto"). */
export const displayName = (p: { firstName: string; lastName: string; currentTitle?: string | null }) =>
  `${p.currentTitle ? p.currentTitle + ' ' : ''}${p.firstName} ${p.lastName}`.trim();

export const AGE_CATEGORIES = ['SUB_8', 'SUB_10', 'SUB_12', 'SUB_14', 'SUB_16', 'SUB_18', 'SUB_20', 'ADULTO', 'SENIOR'] as const;

export const categoryLabel = (c: string) => c.replace('SUB_', 'Sub ').replace('ADULTO', 'Adulto').replace('SENIOR', 'Senior');

/** Opciones del selector de ranking: nacional (federación), las tres modalidades FIDE y ChessQuery por ritmo. */
export const RANKED_TYPES: { type: RankedType; label: string; short: string }[] = [
  { type: 'NATIONAL', label: 'Nacional', short: 'ELO Nac.' },
  { type: 'FIDE_STANDARD', label: 'FIDE clásico', short: 'FIDE' },
  { type: 'FIDE_RAPID', label: 'FIDE rápido', short: 'FIDE rápido' },
  { type: 'FIDE_BLITZ', label: 'FIDE blitz', short: 'FIDE blitz' },
  { type: 'PLATFORM_BULLET', label: 'ChessQuery bala', short: 'CQ bala' },
  { type: 'PLATFORM_BLITZ', label: 'ChessQuery relámpago', short: 'CQ relámpago' },
  { type: 'PLATFORM_RAPID', label: 'ChessQuery rápida', short: 'CQ rápida' },
  { type: 'PLATFORM_CLASSICAL', label: 'ChessQuery clásica', short: 'CQ clásica' },
];
