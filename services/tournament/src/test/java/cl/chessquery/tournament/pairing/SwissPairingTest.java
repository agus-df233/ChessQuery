package cl.chessquery.tournament.pairing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwissPairingTest {

    /** Simula un torneo completo con resultados aleatorios y verifica las reglas en cada ronda. */
    @ParameterizedTest
    @CsvSource({"6,5,1", "7,5,2", "8,5,3", "9,6,4", "12,7,5", "15,6,6", "24,8,7", "41,7,8"})
    void torneoCompletoSinRevanchasUnByePorJugadorYColoresSanos(int players, int rounds, long seed) {
        Random rnd = new Random(seed);
        Sim sim = new Sim(players);
        for (int round = 1; round <= rounds; round++) {
            List<Pair> pairs = SwissPairing.pair(sim.competitors(), round);
            Set<Long> seen = new HashSet<>();
            for (Pair p : pairs) {
                assertThat(seen.add(p.white())).isTrue();
                if (!p.isBye()) {
                    assertThat(seen.add(p.black())).isTrue();
                    assertThat(sim.opponents.get(p.white())).doesNotContain(p.black());
                }
            }
            assertThat(seen).hasSize(players);
            assertThat(pairs.stream().filter(Pair::isBye).count()).isEqualTo(players % 2);
            sim.play(pairs, rnd);
        }
        sim.byes.values().forEach(n -> assertThat(n).isLessThanOrEqualTo(1));
        sim.colors.values().forEach(c -> assertThat(Math.abs(balance(c))).isLessThanOrEqualTo(2));
    }

    @Test
    void primeraRondaMitadContraMitadConColoresAlternados() {
        List<Competitor> field = List.of(c(1, 2000), c(2, 1900), c(3, 1800), c(4, 1700), c(5, 1600));
        List<Pair> pairs = SwissPairing.pair(field, 1);
        assertThat(pairs).containsExactly(new Pair(1, 3L), new Pair(4, 2L), new Pair(5, null));
    }

    @Test
    void holandesEnElGrupoDePuntaje() {
        // 4 jugadores con 1 punto: 1º vs 3º y 2º vs 4º (no 1º vs 2º)
        List<Competitor> field = List.of(c(1, 2000, 2), c(2, 1900, 2), c(3, 1800, 2), c(4, 1700, 2));
        List<Pair> pairs = SwissPairing.pair(field, 2);
        assertThat(pairs).extracting(p -> Set.of(p.white(), p.black())).containsExactly(Set.of(1L, 3L), Set.of(2L, 4L));
    }

    @Test
    void elByeNoSeRepite() {
        List<Competitor> field = List.of(c(1, 2000, 0), c(2, 1900, 0), new Competitor(3, 1500, 0, List.of(), List.of(), true));
        assertThat(SwissPairing.chooseBye(new ArrayList<>(field)).id()).isEqualTo(2);
    }

    @Test
    void coloresQuienNecesitaBlancasLasRecibe() {
        Competitor twoBlacks = new Competitor(1, 2000, 2, List.of(8L, 9L), List.of(false, false), false);
        Competitor twoWhites = new Competitor(2, 1900, 2, List.of(6L, 7L), List.of(true, true), false);
        assertThat(SwissPairing.withColors(new Competitor[] {twoWhites, twoBlacks})).isEqualTo(new Pair(1, 2L));
        assertThat(SwissPairing.compatible(twoBlacks, new Competitor(3, 1, 2, List.of(), List.of(false, false), false), true)).isFalse();
    }

    @Test
    void sinPareoPosibleLanzaError() {
        List<Competitor> field = List.of(new Competitor(1, 2000, 2, List.of(2L), List.of(true), false),
                new Competitor(2, 1900, 0, List.of(1L), List.of(false), false));
        assertThatThrownBy(() -> SwissPairing.pair(field, 2)).isInstanceOf(IllegalStateException.class);
    }

    private static Competitor c(long id, int rating) { return c(id, rating, 0); }

    private static Competitor c(long id, int rating, int half) {
        return new Competitor(id, rating, half, List.of(), List.of(), false);
    }

    private static int balance(List<Boolean> colors) {
        return (int) colors.stream().filter(x -> x).count() * 2 - colors.size();
    }

    private static final class Sim {
        final Map<Long, Integer> half = new HashMap<>();
        final Map<Long, List<Long>> opponents = new HashMap<>();
        final Map<Long, List<Boolean>> colors = new HashMap<>();
        final Map<Long, Integer> byes = new HashMap<>();
        final int n;

        Sim(int n) {
            this.n = n;
            for (long id = 1; id <= n; id++) {
                half.put(id, 0);
                opponents.put(id, new ArrayList<>());
                colors.put(id, new ArrayList<>());
                byes.put(id, 0);
            }
        }

        List<Competitor> competitors() {
            List<Competitor> list = new ArrayList<>();
            for (long id = 1; id <= n; id++) {
                list.add(new Competitor(id, 2500 - (int) id * 10, half.get(id), opponents.get(id), colors.get(id), byes.get(id) > 0));
            }
            return list;
        }

        void play(List<Pair> pairs, Random rnd) {
            for (Pair p : pairs) {
                if (p.isBye()) {
                    half.merge(p.white(), 2, Integer::sum);
                    byes.merge(p.white(), 1, Integer::sum);
                    continue;
                }
                int r = rnd.nextInt(3);
                half.merge(p.white(), r, Integer::sum);
                half.merge(p.black(), 2 - r, Integer::sum);
                opponents.get(p.white()).add(p.black());
                opponents.get(p.black()).add(p.white());
                colors.get(p.white()).add(true);
                colors.get(p.black()).add(false);
            }
        }
    }
}
