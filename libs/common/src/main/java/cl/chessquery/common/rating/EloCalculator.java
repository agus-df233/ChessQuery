package cl.chessquery.common.rating;

import java.util.List;

/**
 * Cálculo ELO de la plataforma (el mismo para partidas en línea y torneos del club).
 * <ul>
 *   <li>Esperado: {@code E = 1 / (1 + 10^((Rrival - R) / 400))}.</li>
 *   <li>Nuevo rating: {@code R + K · Σ(resultado − E)}; en un torneo se suma sobre todas sus partidas con el rating
 *       previo al torneo (como la FIDE), no partida a partida.</li>
 *   <li>K: 40 para quien aún no tiene rating de plataforma, 20 bajo 2400 y 10 desde 2400.</li>
 * </ul>
 * Resultado: 1 gana, 0.5 tablas, 0 pierde. Las partidas no jugadas (bye, no presentación) no se pasan acá.
 */
public final class EloCalculator {

    /** Rating inicial de quien no tiene ninguno. */
    public static final int DEFAULT_RATING = 1500;
    public static final int MIN_RATING = 100;

    private EloCalculator() {}

    public record Game(int opponentRating, double score) {}

    public static double expected(int rating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10, (opponentRating - rating) / 400.0));
    }

    public static int kFactor(int rating, boolean unrated) {
        if (unrated) return 40;
        return rating >= 2400 ? 10 : 20;
    }

    /** Nuevo rating tras una serie de partidas contra rivales con los ratings dados. */
    public static int next(int rating, boolean unrated, List<Game> games) {
        double change = games.stream().mapToDouble(g -> g.score() - expected(rating, g.opponentRating())).sum();
        return Math.max(MIN_RATING, (int) Math.round(rating + kFactor(rating, unrated) * change));
    }

    /** Una sola partida. */
    public static int next(int rating, boolean unrated, int opponentRating, double score) {
        return next(rating, unrated, List.of(new Game(opponentRating, score)));
    }
}
