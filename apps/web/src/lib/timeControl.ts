/**
 * Ritmos de juego. Espejo de `TimeControlCategory` (libs/common): cada jugador tiene un ELO ChessQuery por ritmo y
 * cada partida o torneo actualiza el de su ritmo. Duración estimada = base + 40 × incremento (segundos), con los
 * cortes de Lichess: < 3 min bala, < 8 relámpago, < 25 rápida, desde 25 clásica.
 */
export type TimeControlCategory = 'BULLET' | 'BLITZ' | 'RAPID' | 'CLASSICAL';

export const CATEGORIES: TimeControlCategory[] = ['BULLET', 'BLITZ', 'RAPID', 'CLASSICAL'];

export const CATEGORY_LABEL: Record<TimeControlCategory, string> = {
  BULLET: 'Bala', BLITZ: 'Relámpago', RAPID: 'Rápida', CLASSICAL: 'Clásica',
};

export const categoryOf = (minutes: number, incrementSeconds: number): TimeControlCategory => {
  const estimated = minutes * 60 + 40 * incrementSeconds;
  if (estimated < 180) return 'BULLET';
  if (estimated < 480) return 'BLITZ';
  if (estimated < 1500) return 'RAPID';
  return 'CLASSICAL';
};

/** "Relámpago 3+2". */
export const timeControlLabel = (minutes: number, incrementSeconds: number) =>
  `${CATEGORY_LABEL[categoryOf(minutes, incrementSeconds)]} ${minutes}+${incrementSeconds}`;

/** Ritmos ofrecidos al desafiar ([minutos, incremento]); además se puede elegir uno personalizado. */
export const PRESETS: [number, number][] = [[1, 0], [3, 2], [5, 0], [10, 0], [10, 5], [15, 10], [30, 0]];

/** Límites que acepta la API de partidas (ChallengeRequest). */
export const LIMITS = { minMinutes: 1, maxMinutes: 180, maxIncrement: 180 };
