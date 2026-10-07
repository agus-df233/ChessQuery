package cl.chessquery.common.rating;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TimeControlCategoryTest {

    /** Los presets de la web y los ritmos típicos de torneo caen donde un jugador espera. */
    @ParameterizedTest(name = "{0}+{1} → {2}")
    @CsvSource({
            "1, 0, BULLET", "2, 1, BULLET", "3, 0, BLITZ", "3, 2, BLITZ", "5, 0, BLITZ", "5, 3, BLITZ",
            "10, 0, RAPID", "10, 5, RAPID", "15, 10, RAPID", "25, 0, CLASSICAL", "30, 0, CLASSICAL", "90, 30, CLASSICAL"})
    void clasificaLosRitmosHabituales(int minutes, int increment, TimeControlCategory expected) {
        assertThat(TimeControlCategory.of(minutes * 60, increment)).isEqualTo(expected);
    }

    /** Valores límite: los cortes son 180, 480 y 1500 segundos estimados (exclusivos por arriba). */
    @Test
    void cortesExactos() {
        assertThat(TimeControlCategory.of(179, 0)).isEqualTo(TimeControlCategory.BULLET);
        assertThat(TimeControlCategory.of(180, 0)).isEqualTo(TimeControlCategory.BLITZ);
        assertThat(TimeControlCategory.of(479, 0)).isEqualTo(TimeControlCategory.BLITZ);
        assertThat(TimeControlCategory.of(480, 0)).isEqualTo(TimeControlCategory.RAPID);
        assertThat(TimeControlCategory.of(1499, 0)).isEqualTo(TimeControlCategory.RAPID);
        assertThat(TimeControlCategory.of(1500, 0)).isEqualTo(TimeControlCategory.CLASSICAL);
        assertThat(TimeControlCategory.of(0, 0)).isEqualTo(TimeControlCategory.BULLET);
        assertThat(TimeControlCategory.of(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEqualTo(TimeControlCategory.CLASSICAL);
    }

    @Test
    void tipoDeRatingQueSePublica() {
        assertThat(TimeControlCategory.BLITZ.ratingType()).isEqualTo("PLATFORM_BLITZ");
        assertThat(TimeControlCategory.CLASSICAL.ratingType()).isEqualTo("PLATFORM_CLASSICAL");
    }
}
