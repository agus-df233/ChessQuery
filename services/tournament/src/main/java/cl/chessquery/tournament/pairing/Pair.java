package cl.chessquery.tournament.pairing;

/** Una mesa: {@code black == null} es un bye (punto completo para {@code white}). */
public record Pair(long white, Long black) {
    public boolean isBye() { return black == null; }
}
