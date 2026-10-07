package cl.chessquery.tournament;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee el ritmo de una etiqueta libre como la escriben los organizadores: "90+30", "15 + 10" o "60" (sin incremento).
 * Devuelve {minutos, incremento} o null si no se puede leer o queda fuera de rango (1–300 min, 0–180 s), el mismo
 * criterio que la migración V2.
 */
final class TimeControlLabel {

    private static final Pattern LABEL = Pattern.compile("^\\s*(\\d{1,3})\\s*(?:\\+\\s*(\\d{1,3}))?\\s*$");

    private TimeControlLabel() {}

    static int[] parse(String label) {
        if (label == null) return null;
        Matcher m = LABEL.matcher(label);
        if (!m.matches()) return null;
        int minutes = Integer.parseInt(m.group(1));
        int increment = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (minutes < 1 || minutes > 300 || increment > 180) return null;
        return new int[] {minutes, increment};
    }
}
