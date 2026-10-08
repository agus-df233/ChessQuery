package cl.chessquery.tournament.trf;

import cl.chessquery.tournament.domain.Result;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exporta el torneo en <b>FIDE TRF-16</b> (el formato que aceptan la FIDE y la Federación para homologar).
 * Columnas fijas de la línea 001: número inicial (5-8), sexo (10), título (11-13), nombre "Apellido, Nombre"
 * (15-47), rating (49-52), federación (54-56), FIDE id (58-68), fecha de nacimiento (70-79), puntos (81-84),
 * lugar (86-89) y, desde la columna 92, un bloque de 10 caracteres por ronda: rival (4), color (w/b/-) y
 * resultado (1, 0, =, + o - por no presentación, U para bye).
 */
public final class TrfExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private TrfExporter() {}

    public record Header(String name, String city, String federation, LocalDate start, LocalDate end, String type,
                         String timeControl, int rounds) {}

    /** Jugador en orden de número inicial (1 = mayor rating). */
    public record Entry(int startRank, String firstName, String lastName, String gender, String title, int rating,
                        String fideId, Integer birthYear, double points, int place) {}

    /** Mesa de una ronda, con los números iniciales. {@code black == null} es bye. */
    public record Board(int round, int white, Integer black, Result result) {}

    public static String export(Header h, List<Entry> entries, Map<Integer, List<Board>> boardsByRank) {
        StringBuilder out = new StringBuilder();
        line(out, "012", h.name());
        line(out, "022", h.city());
        line(out, "032", h.federation());
        line(out, "042", h.start() == null ? null : DATE.format(h.start()));
        line(out, "052", h.end() == null ? null : DATE.format(h.end()));
        line(out, "062", String.valueOf(entries.size()));
        line(out, "092", h.type());
        line(out, "122", h.timeControl());
        for (Entry e : entries) {
            out.append(playerLine(e, boardsByRank.getOrDefault(e.startRank(), List.of()), h.rounds())).append('\n');
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String code, String value) {
        if (value != null && !value.isBlank()) out.append(code).append(' ').append(cell(value)).append('\n');
    }

    /**
     * Texto escrito por personas (nombres del torneo, la ciudad o los jugadores) neutralizado para el archivo:
     * <ul>
     *   <li>los saltos de línea y tabulaciones pasan a espacio: un nombre no puede inyectar líneas (p. ej. un
     *       jugador "001" falso);</li>
     *   <li>si empieza con {@code = + - @} se antepone {@code '}: el TRF se abre a menudo en una planilla y ese
     *       comienzo se ejecutaría como fórmula (recomendación de OWASP para «CSV injection»).</li>
     * </ul>
     */
    static String cell(String value) {
        String flat = value.replaceAll("[\\r\\n\\t]", " ");
        return !flat.isEmpty() && "=+-@".indexOf(flat.charAt(0)) >= 0 ? "'" + flat : flat;
    }

    static String playerLine(Entry e, List<Board> boards, int rounds) {
        StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "001 %4d %1s%3s %-33.33s %4s %3s %11s %10s %4.1f %4d ",
                e.startRank(), sex(e.gender()), e.title() == null ? "" : e.title(), cell(e.lastName() + ", " + e.firstName()),
                e.rating() > 0 ? String.valueOf(e.rating()) : "", "CHI", e.fideId() == null ? "" : e.fideId(),
                e.birthYear() == null ? "" : e.birthYear() + "/00/00", e.points(), e.place()));
        for (int r = 1; r <= rounds; r++) {
            s.append(' ').append(roundBlock(e.startRank(), boardOf(boards, r)));
        }
        return s.toString().stripTrailing();
    }

    private static Board boardOf(List<Board> boards, int round) {
        return boards.stream().filter(b -> b.round() == round).findFirst().orElse(null);
    }

    /** 9 caracteres + separador: "  12 w 1 ". Sin mesa (ronda no jugada aún): en blanco. */
    static String roundBlock(int me, Board b) {
        if (b == null || b.result() == null) return "         ";
        if (b.black() == null) return "0000 - U ";
        boolean white = b.white() == me;
        int rival = white ? b.black() : b.white();
        return String.format(Locale.ROOT, "%4d %s %s ", rival, white ? "w" : "b", code(b.result(), white));
    }

    static String code(Result r, boolean white) {
        int mine = r.halfPoints(white);
        if (!r.overTheBoard()) return mine == 2 ? "+" : "-";
        return switch (mine) {
            case 2 -> "1";
            case 1 -> "=";
            default -> "0";
        };
    }

    private static String sex(String gender) {
        if ("F".equals(gender)) return "w";
        return "M".equals(gender) ? "m" : "";
    }
}
