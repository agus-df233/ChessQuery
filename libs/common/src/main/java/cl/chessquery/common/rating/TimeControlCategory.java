package cl.chessquery.common.rating;

/**
 * Ritmo de juego de una partida o torneo, que define <b>qué ELO ChessQuery</b> se usa y se actualiza: cada jugador
 * tiene uno por ritmo, como en Lichess y Chess.com (una partida bala no dice nada del nivel en partidas largas).
 * <p>
 * Se clasifica por la duración estimada de la partida, {@code base + 40 × incremento} (en segundos, suponiendo 40
 * jugadas por lado), con los mismos cortes que Lichess para que el ELO sea comparable con el de esa plataforma:
 * menos de 3 min bala, menos de 8 relámpago, menos de 25 rápido y desde 25 clásico. Ejemplos: 1+0 bala, 3+2
 * relámpago, 10+5 y 15+10 rápido, 30+0 y 90+30 clásico.
 */
public enum TimeControlCategory {
    BULLET, BLITZ, RAPID, CLASSICAL;

    /** Tipo de rating que se publica en {@code elo.updated} y que users guarda (p. ej. {@code PLATFORM_BLITZ}). */
    public String ratingType() {
        return "PLATFORM_" + name();
    }

    public static TimeControlCategory of(int baseSeconds, int incrementSeconds) {
        long estimated = (long) baseSeconds + 40L * incrementSeconds;
        if (estimated < 180) return BULLET;
        if (estimated < 480) return BLITZ;
        if (estimated < 1500) return RAPID;
        return CLASSICAL;
    }
}
