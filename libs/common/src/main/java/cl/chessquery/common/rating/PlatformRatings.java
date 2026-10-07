package cl.chessquery.common.rating;

/**
 * ELO ChessQuery de un jugador, uno por ritmo ({@link TimeControlCategory}); null = aún no juega ese ritmo por rating.
 * Es el contrato compartido entre users (que lo guarda) y game/tournament (que lo leen para empezar una partida o
 * sembrar un torneo).
 */
public record PlatformRatings(Integer bullet, Integer blitz, Integer rapid, Integer classical) {

    public Integer of(TimeControlCategory category) {
        return switch (category) {
            case BULLET -> bullet;
            case BLITZ -> blitz;
            case RAPID -> rapid;
            case CLASSICAL -> classical;
        };
    }
}
