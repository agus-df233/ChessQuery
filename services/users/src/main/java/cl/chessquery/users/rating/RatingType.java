package cl.chessquery.users.rating;

/**
 * Modalidades de rating que la plataforma conoce. Cada una tiene su columna snapshot en
 * {@code player} y su serie en {@code rating_history}. El orden no importa.
 */
public enum RatingType {
    NATIONAL, FIDE_STANDARD, FIDE_RAPID, FIDE_BLITZ, PLATFORM,
    LICHESS_BULLET, LICHESS_BLITZ, LICHESS_RAPID, LICHESS_CLASSICAL,
    CHESSCOM_BULLET, CHESSCOM_BLITZ, CHESSCOM_RAPID, CHESSCOM_DAILY
}
