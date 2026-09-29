package cl.chessquery.tournament.pairing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 10})
    void todosContraTodosUnaVez(int n) {
        List<Long> seeds = LongStream.rangeClosed(1, n).boxed().toList();
        Set<Set<Long>> games = new HashSet<>();
        int byes = 0;
        for (int round = 1; round <= RoundRobin.rounds(n); round++) {
            List<Pair> pairs = RoundRobin.round(seeds, round);
            Set<Long> inRound = new HashSet<>();
            for (Pair p : pairs) {
                assertThat(inRound.add(p.white())).isTrue();
                if (p.isBye()) { byes++; continue; }
                assertThat(inRound.add(p.black())).isTrue();
                assertThat(games.add(Set.of(p.white(), p.black()))).as("partida repetida").isTrue();
            }
            assertThat(inRound).hasSize(n);
            assertThat(pairs.get(pairs.size() - 1).isBye() || n % 2 == 0).isTrue();
        }
        assertThat(games).hasSize(n * (n - 1) / 2);
        assertThat(byes).isEqualTo(n % 2 == 0 ? 0 : n);
    }
}
