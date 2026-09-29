package cl.chessquery.tournament.standings;

import cl.chessquery.tournament.domain.Format;
import cl.chessquery.tournament.domain.Result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabla con desempates (en medios puntos internamente; la API los muestra como 2.5):
 * <ul>
 *   <li><b>Buchholz</b>: suma de los puntos finales de los rivales.</li>
 *   <li><b>Buchholz corte 1</b>: lo mismo sin el rival de menos puntos.</li>
 *   <li><b>Sonneborn-Berger</b>: puntos de los rivales vencidos + la mitad de los empatados.</li>
 *   <li><b>Victorias</b>: partidas ganadas en el tablero.</li>
 * </ul>
 * Orden en suizo: puntos, Buchholz corte 1, Buchholz, Sonneborn-Berger, victorias, rating.
 * En round robin (todos juegan con todos, el Buchholz no discrimina): puntos, Sonneborn-Berger, victorias, rating.
 * El bye y las no presentaciones suman puntos pero no cuentan como rival para los desempates.
 */
public final class Standings {

    private Standings() {}

    /** Una mesa con resultado. {@code black == null} es bye. */
    public record Game(long white, Long black, Result result) {}

    public record Row(int position, long playerId, double points, double buchholzCut1, double buchholz,
                      double sonnebornBerger, int wins, int rating, int played) {}

    private static final class Acc {
        int half;
        int wins;
        int played;
        final List<long[]> faced = new ArrayList<>(); // {rivalId, mis medios puntos contra él}
    }

    public static List<Row> compute(Map<Long, Integer> ratings, List<Game> games, Format format) {
        Map<Long, Acc> acc = new HashMap<>();
        ratings.keySet().forEach(id -> acc.put(id, new Acc()));
        games.stream().filter(g -> g.result() != null).forEach(g -> add(acc, g));
        List<Row> rows = new ArrayList<>(ratings.keySet().stream().map(id -> row(id, acc, ratings.get(id))).toList());
        rows.sort(order(format));
        List<Row> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            ranked.add(new Row(i + 1, r.playerId(), r.points(), r.buchholzCut1(), r.buchholz(), r.sonnebornBerger(),
                    r.wins(), r.rating(), r.played()));
        }
        return ranked;
    }

    private static void add(Map<Long, Acc> acc, Game g) {
        Acc white = acc.computeIfAbsent(g.white(), id -> new Acc());
        white.half += g.result().halfPoints(true);
        if (g.black() == null) return;
        Acc black = acc.computeIfAbsent(g.black(), id -> new Acc());
        black.half += g.result().halfPoints(false);
        if (!g.result().overTheBoard()) return;
        white.played++;
        black.played++;
        if (g.result() == Result.WHITE_WINS) white.wins++;
        if (g.result() == Result.BLACK_WINS) black.wins++;
        white.faced.add(new long[] {g.black(), g.result().halfPoints(true)});
        black.faced.add(new long[] {g.white(), g.result().halfPoints(false)});
    }

    private static Row row(long id, Map<Long, Acc> acc, int rating) {
        Acc me = acc.get(id);
        List<Integer> rivalHalves = me.faced.stream().map(f -> acc.get(f[0]).half).sorted().toList();
        int buchholz = rivalHalves.stream().mapToInt(Integer::intValue).sum();
        int cut1 = rivalHalves.isEmpty() ? 0 : buchholz - rivalHalves.get(0);
        int sbQuarter = me.faced.stream().mapToInt(f -> acc.get(f[0]).half * (int) f[1]).sum(); // medio·medio = cuartos
        return new Row(0, id, me.half / 2.0, cut1 / 2.0, buchholz / 2.0, sbQuarter / 4.0, me.wins, rating, me.played);
    }

    private static Comparator<Row> order(Format format) {
        Comparator<Row> byPoints = Comparator.comparingDouble(Row::points).reversed();
        Comparator<Row> tail = Comparator.comparingDouble(Row::sonnebornBerger).reversed()
                .thenComparing(Comparator.comparingInt(Row::wins).reversed())
                .thenComparing(Comparator.comparingInt(Row::rating).reversed())
                .thenComparingLong(Row::playerId);
        if (format == Format.ROUND_ROBIN) return byPoints.thenComparing(tail);
        return byPoints.thenComparing(Comparator.comparingDouble(Row::buchholzCut1).reversed())
                .thenComparing(Comparator.comparingDouble(Row::buchholz).reversed())
                .thenComparing(tail);
    }
}
