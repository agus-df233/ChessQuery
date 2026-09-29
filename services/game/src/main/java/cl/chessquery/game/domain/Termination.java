package cl.chessquery.game.domain;

/** Cómo terminó la partida (se muestra al jugador y va a la etiqueta Termination del PGN). */
public enum Termination {
    CHECKMATE("jaque mate"),
    RESIGNATION("abandono"),
    TIMEOUT("tiempo"),
    STALEMATE("ahogado"),
    INSUFFICIENT_MATERIAL("material insuficiente"),
    THREEFOLD_REPETITION("triple repetición"),
    FIFTY_MOVE_RULE("regla de 50 jugadas"),
    AGREEMENT("tablas de mutuo acuerdo");

    private final String label;

    Termination(String label) { this.label = label; }

    public String label() { return label; }
}
