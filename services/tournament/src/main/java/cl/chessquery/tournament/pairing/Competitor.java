package cl.chessquery.tournament.pairing;

import java.util.List;

/**
 * Estado de un jugador antes de parear una ronda.
 *
 * @param halfPoints medios puntos acumulados
 * @param opponents  rivales ya enfrentados (no se repiten)
 * @param colors     colores de sus partidas jugadas, en orden ({@code true} = blancas)
 * @param hadBye     ya recibió un bye (no recibe otro mientras se pueda evitar)
 */
public record Competitor(long id, int rating, int halfPoints, List<Long> opponents, List<Boolean> colors, boolean hadBye) {

    /** Blancas menos negras. */
    public int colorBalance() {
        return (int) colors.stream().filter(c -> c).count() * 2 - colors.size();
    }

    public Boolean lastColor() {
        return colors.isEmpty() ? null : colors.get(colors.size() - 1);
    }

    /** Color que <b>debe</b> recibir (desbalance de 2 o el mismo color dos veces seguidas), o null si puede ambos. */
    public Boolean mustHave() {
        if (colorBalance() >= 2) return false;
        if (colorBalance() <= -2) return true;
        int n = colors.size();
        if (n >= 2 && colors.get(n - 1).equals(colors.get(n - 2))) return !colors.get(n - 1);
        return null;
    }

    public boolean played(long other) {
        return opponents.contains(other);
    }
}
