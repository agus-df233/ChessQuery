package cl.chessquery.game.domain;

/** PENDING: desafío sin responder. ACTIVE: en juego. El resto son estados finales. */
public enum GameStatus {
    PENDING, ACTIVE, FINISHED, DECLINED, CANCELLED, EXPIRED;

    public boolean isOver() {
        return this != PENDING && this != ACTIVE;
    }
}
