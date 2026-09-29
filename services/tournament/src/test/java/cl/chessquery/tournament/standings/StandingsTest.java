package cl.chessquery.tournament.standings;

import cl.chessquery.tournament.domain.Format;
import cl.chessquery.tournament.domain.Result;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StandingsTest {

    /**
     * 4 jugadores, 3 rondas. Resultados:
     * R1: 1-2 1-0, 3-4 ½; R2: 1-3 ½, 4-2 1-0; R3: 4-1 0-1, 2-3 0-1 (NP, no cuenta como rival).
     * Puntos: 1=2.5, 3=2.0, 4=1.5, 2=0.
     */
    @Test
    void puntosYDesempates() {
        Map<Long, Integer> ratings = new LinkedHashMap<>(Map.of(1L, 2000, 2L, 1900, 3L, 1800, 4L, 1700));
        List<Standings.Game> games = List.of(
                new Standings.Game(1, 2L, Result.WHITE_WINS), new Standings.Game(3, 4L, Result.DRAW),
                new Standings.Game(1, 3L, Result.DRAW), new Standings.Game(4, 2L, Result.WHITE_WINS),
                new Standings.Game(4, 1L, Result.BLACK_WINS), new Standings.Game(2, 3L, Result.BLACK_FORFEIT_WIN),
                new Standings.Game(5, null, null));
        List<Standings.Row> rows = Standings.compute(ratings, games, Format.SWISS);
        assertThat(rows).extracting(Standings.Row::playerId).containsExactly(1L, 3L, 4L, 2L);
        Standings.Row first = rows.get(0);
        assertThat(first.points()).isEqualTo(2.5);
        assertThat(first.buchholz()).isEqualTo(0 + 2.0 + 1.5);      // rivales 2, 3, 4
        assertThat(first.buchholzCut1()).isEqualTo(3.5);             // sin el de 0 puntos
        assertThat(first.sonnebornBerger()).isEqualTo(0 + 1.0 + 1.5); // gana a 2 y 4, empata con 3
        assertThat(first.wins()).isEqualTo(2);
        Standings.Row third = rows.get(1);
        assertThat(third.played()).isEqualTo(2);                     // la no presentación no se jugó
        assertThat(third.points()).isEqualTo(2.0);
    }

    @Test
    void roundRobinOrdenaPorSonnebornBerger() {
        Map<Long, Integer> ratings = new LinkedHashMap<>(Map.of(1L, 1500, 2L, 1500, 3L, 1500));
        // 1 y 2 empatan en puntos (1 cada uno); 1 venció al 3 (1 pt) y 2 venció al 1... ciclo
        List<Standings.Game> games = List.of(new Standings.Game(1, 3L, Result.WHITE_WINS),
                new Standings.Game(2, 1L, Result.WHITE_WINS), new Standings.Game(3, 2L, Result.DRAW));
        List<Standings.Row> rows = Standings.compute(ratings, games, Format.ROUND_ROBIN);
        assertThat(rows.get(0).playerId()).isEqualTo(2L); // 1.5 puntos
        assertThat(rows).extracting(Standings.Row::position).containsExactly(1, 2, 3);
    }
}
