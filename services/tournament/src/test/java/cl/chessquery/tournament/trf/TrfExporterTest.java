package cl.chessquery.tournament.trf;

import cl.chessquery.tournament.domain.Result;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrfExporterTest {

    @Test
    void columnasFijasDelFormatoTrf16() {
        TrfExporter.Entry ana = new TrfExporter.Entry(1, "Ana", "Soto", "F", "WFM", 1850, "3400001", 1990, 1.5, 1);
        List<TrfExporter.Board> boards = List.of(new TrfExporter.Board(1, 1, 2, Result.WHITE_WINS),
                new TrfExporter.Board(2, 3, 1, Result.DRAW), new TrfExporter.Board(3, 1, null, Result.BYE));
        String line = TrfExporter.playerLine(ana, boards, 4);
        assertThat(line.substring(0, 3)).isEqualTo("001");
        assertThat(line.substring(4, 8)).isEqualTo("   1");
        assertThat(line.charAt(9)).isEqualTo('w');
        assertThat(line.substring(10, 13)).isEqualTo("WFM");
        assertThat(line.substring(14, 47).trim()).isEqualTo("Soto, Ana");
        assertThat(line.substring(48, 52)).isEqualTo("1850");
        assertThat(line.substring(53, 56)).isEqualTo("CHI");
        assertThat(line.substring(57, 68).trim()).isEqualTo("3400001");
        assertThat(line.substring(69, 79)).isEqualTo("1990/00/00");
        assertThat(line.substring(80, 84)).isEqualTo(" 1.5");
        assertThat(line.substring(85, 89)).isEqualTo("   1");
        assertThat(line.substring(91, 99)).isEqualTo("   2 w 1");
        assertThat(line.substring(101, 109)).isEqualTo("   3 b =");
        assertThat(line.substring(111, 119)).isEqualTo("0000 - U");
        assertThat(line).hasSize(119); // la ronda 4 (sin jugar) queda en blanco y se recorta
    }

    @Test
    void cabeceraYNoPresentaciones() {
        String trf = TrfExporter.export(new TrfExporter.Header("Abierto", "Santiago", "CHI", LocalDate.of(2026, 10, 3),
                null, "Suizo", "60+30", 1), List.of(), Map.of());
        assertThat(trf).startsWith("012 Abierto\n022 Santiago\n032 CHI\n042 2026/10/03\n062 0\n092 Suizo\n122 60+30\n");
        assertThat(TrfExporter.code(Result.WHITE_FORFEIT_WIN, true)).isEqualTo("+");
        assertThat(TrfExporter.code(Result.WHITE_FORFEIT_WIN, false)).isEqualTo("-");
        assertThat(TrfExporter.code(Result.BLACK_WINS, false)).isEqualTo("1");
        assertThat(TrfExporter.roundBlock(1, null)).isBlank();
    }
}
