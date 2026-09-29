package cl.chessquery.tournament.pairing;

import java.util.ArrayList;
import java.util.List;

/**
 * Todos contra todos por el método del círculo (tablas de Berger): el último número queda fijo y el resto rota.
 * Con cantidad impar se agrega un "descanso" (bye). El orden de siembra es el de rating al iniciar.
 */
public final class RoundRobin {

    private RoundRobin() {}

    /** Rondas necesarias: n − 1 si n es par, n si es impar. */
    public static int rounds(int players) {
        return players % 2 == 0 ? players - 1 : players;
    }

    /** Mesas de la ronda {@code round} (desde 1) para los jugadores en orden de siembra. */
    public static List<Pair> round(List<Long> seeds, int round) {
        List<Long> slots = new ArrayList<>(seeds);
        if (slots.size() % 2 == 1) slots.add(null); // descanso
        int n = slots.size();
        int k = round - 1;
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < n / 2; i++) {
            int a = (k + i) % (n - 1);
            int b = i == 0 ? n - 1 : (k + n - 1 - i) % (n - 1);
            boolean aWhite = i != 0 || k % 2 == 0;
            addBoard(pairs, slots.get(aWhite ? a : b), slots.get(aWhite ? b : a));
        }
        pairs.sort((x, y) -> Boolean.compare(x.isBye(), y.isBye())); // el bye al final
        return pairs;
    }

    private static void addBoard(List<Pair> pairs, Long white, Long black) {
        if (white == null) pairs.add(new Pair(black, null));
        else pairs.add(new Pair(white, black));
    }
}
