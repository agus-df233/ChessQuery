package cl.chessquery.tournament.domain;

/**
 * Resultado de una mesa. Los puntos van en <b>medios puntos</b> (2 = 1 punto) para sumar sin decimales.
 * {@code overTheBoard}: la partida se jugó (cuenta para colores, victorias y rating); las no presentaciones y el
 * bye no se juegan.
 */
public enum Result {
    WHITE_WINS(2, 0, true, "1-0"),
    BLACK_WINS(0, 2, true, "0-1"),
    DRAW(1, 1, true, "½-½"),
    WHITE_FORFEIT_WIN(2, 0, false, "1-0 NP"),
    BLACK_FORFEIT_WIN(0, 2, false, "0-1 NP"),
    DOUBLE_FORFEIT(0, 0, false, "0-0 NP"),
    BYE(2, 0, false, "bye");

    private final int whiteHalfPoints;
    private final int blackHalfPoints;
    private final boolean overTheBoard;
    private final String label;

    Result(int whiteHalfPoints, int blackHalfPoints, boolean overTheBoard, String label) {
        this.whiteHalfPoints = whiteHalfPoints;
        this.blackHalfPoints = blackHalfPoints;
        this.overTheBoard = overTheBoard;
        this.label = label;
    }

    public int halfPoints(boolean white) { return white ? whiteHalfPoints : blackHalfPoints; }

    public boolean overTheBoard() { return overTheBoard; }

    public String label() { return label; }
}
