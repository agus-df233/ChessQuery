import type { Ratings, RatingType } from '../api/types';

/** Modalidades agrupadas por fuente para pintar la grilla de ratings y el selector del gráfico. */
export const RATING_GROUPS: { source: string; items: { key: keyof Ratings; type: RatingType; label: string }[] }[] = [
  { source: 'Federado', items: [
    { key: 'national', type: 'NATIONAL', label: 'ELO Nacional' },
    { key: 'fideStandard', type: 'FIDE_STANDARD', label: 'FIDE Estándar' },
    { key: 'fideRapid', type: 'FIDE_RAPID', label: 'FIDE Rápidas' },
    { key: 'fideBlitz', type: 'FIDE_BLITZ', label: 'FIDE Blitz' },
  ]},
  { source: 'ChessQuery', items: [{ key: 'platform', type: 'PLATFORM', label: 'Plataforma' }] },
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
