package cl.chessquery.tournament;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeControlLabelTest {

    @Test
    void leeLasFormasHabituales() {
        assertThat(TimeControlLabel.parse("90+30")).containsExactly(90, 30);
        assertThat(TimeControlLabel.parse(" 15 + 10 ")).containsExactly(15, 10);
        assertThat(TimeControlLabel.parse("60")).containsExactly(60, 0);
    }

    /** Valores límite y entradas que no son un ritmo: se ignoran (el torneo cuenta como rápido). */
    @Test
    void descartaLoIlegibleOFueraDeRango() {
        assertThat(TimeControlLabel.parse(null)).isNull();
        assertThat(TimeControlLabel.parse("")).isNull();
        assertThat(TimeControlLabel.parse("90 min + 30 s")).isNull();
        assertThat(TimeControlLabel.parse("0+5")).isNull();
        assertThat(TimeControlLabel.parse("301")).isNull();
        assertThat(TimeControlLabel.parse("300+180")).containsExactly(300, 180);
        assertThat(TimeControlLabel.parse("10+181")).isNull();
        assertThat(TimeControlLabel.parse("1+0")).containsExactly(1, 0);
    }
}
