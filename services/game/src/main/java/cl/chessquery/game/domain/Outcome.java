package cl.chessquery.game.domain;

/** Resultado final de una partida. */
public enum Outcome {
    WHITE_WINS("1-0"), BLACK_WINS("0-1"), DRAW("1/2-1/2");

    private final String pgn;

    Outcome(String pgn) { this.pgn = pgn; }

    public String pgn() { return pgn; }

    /** Puntos del jugador (1, 0.5 o 0). */
    public double scoreFor(boolean white) {
        if (this == DRAW) return 0.5;
        return (this == WHITE_WINS) == white ? 1 : 0;
    }

    public static Outcome winner(boolean white) {
        return white ? WHITE_WINS : BLACK_WINS;
    }
}
