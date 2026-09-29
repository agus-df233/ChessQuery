package cl.chessquery.tournament.pairing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pareo suizo (inspirado en el sistema holandés de la FIDE, simplificado para torneos de club):
 * <ol>
 *   <li>Orden: puntos, luego rating. Ronda 1: la mitad de arriba contra la de abajo, colores alternados por mesa.</li>
 *   <li>Si el número es impar, el bye va al de menos puntos que aún no haya tenido bye.</li>
 *   <li>Desde la ronda 2, cada jugador (de arriba hacia abajo) busca rival en su grupo de puntaje, empezando por la
 *       mitad del grupo (1º vs n/2+1, como el holandés); si no hay, baja al grupo siguiente. <b>Nunca revanchas</b>:
 *       se resuelve con backtracking.</li>
 *   <li>Colores: primero se exige que nadie reciba el mismo color 3 veces ni un desbalance de 3; si eso impide
 *       parear, se relaja esa condición (nunca la de revanchas).</li>
 * </ol>
 * Puro y determinista: no toca base de datos. Lo prueban {@code SwissPairingTest}.
 */
public final class SwissPairing {

    /** Tope de pasos del backtracking para no colgar la API con casos imposibles. */
    private static final int MAX_STEPS = 500_000;

    private SwissPairing() {}

    public static List<Pair> pair(List<Competitor> players, int roundNumber) {
        List<Competitor> ranked = new ArrayList<>(players);
        ranked.sort(RANKING);
        return roundNumber == 1 ? firstRound(ranked) : laterRound(ranked);
    }

    static final Comparator<Competitor> RANKING = Comparator.comparingInt(Competitor::halfPoints).reversed()
            .thenComparing(Comparator.comparingInt(Competitor::rating).reversed())
            .thenComparingLong(Competitor::id);

    private static List<Pair> firstRound(List<Competitor> ranked) {
        List<Pair> pairs = new ArrayList<>();
        int half = ranked.size() / 2;
        for (int i = 0; i < half; i++) {
            long top = ranked.get(i).id();
            long bottom = ranked.get(i + half).id();
            pairs.add(i % 2 == 0 ? new Pair(top, bottom) : new Pair(bottom, top));
        }
        if (ranked.size() % 2 == 1) pairs.add(new Pair(ranked.get(ranked.size() - 1).id(), null));
        return pairs;
    }

    private static List<Pair> laterRound(List<Competitor> ranked) {
        List<Competitor> pool = new ArrayList<>(ranked);
        Competitor bye = ranked.size() % 2 == 1 ? chooseBye(ranked) : null;
        if (bye != null) pool.remove(bye);
        List<Competitor[]> matched = solve(pool, true);
        if (matched == null) matched = solve(pool, false);
        if (matched == null) {
            throw new IllegalStateException("No hay pareo posible sin repetir rivales");
        }
        List<Pair> pairs = new ArrayList<>(matched.stream().map(SwissPairing::withColors).toList());
        if (bye != null) pairs.add(new Pair(bye.id(), null));
        return pairs;
    }

    /** El de menos puntos (y menor rating) que no tuvo bye; si todos lo tuvieron, el último. */
    static Competitor chooseBye(List<Competitor> ranked) {
        for (int i = ranked.size() - 1; i >= 0; i--) {
            if (!ranked.get(i).hadBye()) return ranked.get(i);
        }
        return ranked.get(ranked.size() - 1);
    }

    private static List<Competitor[]> solve(List<Competitor> pool, boolean strictColors) {
        return new Search(strictColors).run(pool);
    }

    /** Backtracking: el primero sin pareja prueba candidatos en orden de preferencia. */
    private static final class Search {
        private final boolean strictColors;
        private int steps;

        Search(boolean strictColors) { this.strictColors = strictColors; }

        List<Competitor[]> run(List<Competitor> remaining) {
            if (remaining.isEmpty()) return new ArrayList<>();
            if (++steps > MAX_STEPS) return null;
            Competitor first = remaining.get(0);
            for (Competitor candidate : candidates(remaining)) {
                if (!compatible(first, candidate, strictColors)) continue;
                List<Competitor> rest = new ArrayList<>(remaining);
                rest.remove(first);
                rest.remove(candidate);
                List<Competitor[]> tail = run(rest);
                if (tail != null) {
                    tail.add(0, new Competitor[] {first, candidate});
                    return tail;
                }
            }
            return null;
        }
    }

    /**
     * Candidatos para el primero de la lista: primero su grupo de puntaje empezando por la mitad (holandés), luego
     * los grupos más cercanos en puntaje, en orden de ranking.
     */
    static List<Competitor> candidates(List<Competitor> remaining) {
        Competitor first = remaining.get(0);
        List<Competitor> others = remaining.subList(1, remaining.size());
        List<Competitor> group = others.stream().filter(c -> c.halfPoints() == first.halfPoints()).toList();
        int ideal = (group.size() + 1) / 2 - 1; // en un grupo de 4 (con el primero), el ideal es el 3º
        List<Competitor> ordered = new ArrayList<>(others);
        ordered.sort(Comparator
                .comparingInt((Competitor c) -> Math.abs(c.halfPoints() - first.halfPoints()))
                .thenComparingInt(c -> group.contains(c) ? Math.abs(group.indexOf(c) - ideal) : others.indexOf(c)));
        return ordered;
    }

    static boolean compatible(Competitor a, Competitor b, boolean strictColors) {
        if (a.played(b.id())) return false;
        if (!strictColors) return true;
        Boolean ma = a.mustHave();
        return ma == null || !ma.equals(b.mustHave());
    }

    /** Colores: manda quien los necesita; luego quien tuvo menos blancas; luego alternar respecto de la última. */
    static Pair withColors(Competitor[] pair) {
        Competitor a = pair[0];
        Competitor b = pair[1];
        return aGetsWhite(a, b) ? new Pair(a.id(), b.id()) : new Pair(b.id(), a.id());
    }

    private static boolean aGetsWhite(Competitor a, Competitor b) {
        if (a.mustHave() != null) return a.mustHave();
        if (b.mustHave() != null) return !b.mustHave();
        if (a.colorBalance() != b.colorBalance()) return a.colorBalance() < b.colorBalance();
        Boolean lastA = a.lastColor();
        if (lastA != null && !lastA.equals(b.lastColor())) return !lastA;
        return lastA == null || !lastA; // mismo historial: el mejor clasificado alterna
    }
}
