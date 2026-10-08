/**
 * Reglas del asistente de bienvenida, separadas del componente para probarlas como caja negra (valores límite).
 * Los formatos son los mismos que valida users: un username de Lichess/Chess.com tiene 2 a 30 letras, números,
 * «_» o «-» (PlayerDtos.USERNAME) y el id federativo es numérico.
 */
import type { UpdateProfileRequest } from '../../api/types';
import { PRESETS, categoryOf, type TimeControlCategory } from '../../lib/timeControl';

export interface WelcomeAnswers {
  category: TimeControlCategory | null;
  federationId: string;
  lichess: string;
  chesscom: string;
  region: string;
}

export const EMPTY_ANSWERS: WelcomeAnswers = { category: null, federationId: '', lichess: '', chesscom: '', region: '' };

const USERNAME = /^[A-Za-z0-9_-]{2,30}$/;
const FEDERATION_ID = /^\d{1,10}$/;

/** Problemas de los campos opcionales; vacío si se puede avanzar. */
export const accountErrors = (a: WelcomeAnswers): string[] => [
  a.federationId && !FEDERATION_ID.test(a.federationId) ? 'El id federativo son solo números (hasta 10 dígitos).' : '',
  a.lichess && !USERNAME.test(a.lichess) ? 'Usuario de Lichess inválido: 2 a 30 letras, números, «_» o «-».' : '',
  a.chesscom && !USERNAME.test(a.chesscom) ? 'Usuario de Chess.com inválido: 2 a 30 letras, números, «_» o «-».' : '',
].filter(Boolean);

/** Lo que se guarda en el perfil al terminar: solo lo que el jugador completó, más la marca de bienvenida. */
export const welcomeRequest = (a: WelcomeAnswers): UpdateProfileRequest => ({
  welcomed: true,
  ...(a.category ? { preferredCategory: a.category } : {}),
  ...(a.region ? { region: a.region } : {}),
  ...(a.lichess ? { lichessUsername: a.lichess } : {}),
  ...(a.chesscom ? { chesscomUsername: a.chesscom } : {}),
});

/** Ejemplos de cada ritmo para que alguien nuevo entienda la diferencia: «3+2, 5+0». */
export const examplesOf = (category: TimeControlCategory) =>
  PRESETS.filter(([m, i]) => categoryOf(m, i) === category).map(([m, i]) => `${m}+${i}`).join(', ');
