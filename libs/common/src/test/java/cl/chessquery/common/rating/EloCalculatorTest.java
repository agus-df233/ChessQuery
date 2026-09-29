package cl.chessquery.common.rating;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EloCalculatorTest {

    @Test
    void esperadoSimetrico() {
        assertThat(EloCalculator.expected(1500, 1500)).isEqualTo(0.5);
        assertThat(EloCalculator.expected(1900, 1500)).isCloseTo(0.909, within(0.001));
        assertThat(EloCalculator.expected(1500, 1900) + EloCalculator.expected(1900, 1500)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void factorK() {
        assertThat(EloCalculator.kFactor(1500, true)).isEqualTo(40);
        assertThat(EloCalculator.kFactor(2399, false)).isEqualTo(20);
        assertThat(EloCalculator.kFactor(2400, false)).isEqualTo(10);
    }

    @Test
    void partidaSueltaYTorneo() {
        assertThat(EloCalculator.next(1500, false, 1500, 1.0)).isEqualTo(1510);
        assertThat(EloCalculator.next(1500, false, 1500, 0.5)).isEqualTo(1500);
        assertThat(EloCalculator.next(1500, true, 1500, 0.0)).isEqualTo(1480);
        // Torneo: 2 de 3 contra rivales de 1500 → +20·(2 − 1.5) = +10
        assertThat(EloCalculator.next(1500, false, List.of(new EloCalculator.Game(1500, 1), new EloCalculator.Game(1500, 1),
                new EloCalculator.Game(1500, 0)))).isEqualTo(1510);
        assertThat(EloCalculator.next(105, true, 105, 0.0)).isEqualTo(EloCalculator.MIN_RATING); // 105 − 20 → piso 100
    }
}
