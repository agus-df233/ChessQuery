package cl.chessquery.users.ranking;

import cl.chessquery.users.events.Payloads;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgeCategoryTest {

    @Test
    void categoriesFromBirthDate() {
        assertThat(AgeCategory.fromBirthDate(null)).isEqualTo(AgeCategory.ADULTO);
        assertThat(AgeCategory.fromBirthDate(LocalDate.now().minusYears(9))).isEqualTo(AgeCategory.SUB_10);
        assertThat(AgeCategory.fromBirthDate(LocalDate.now().minusYears(70))).isEqualTo(AgeCategory.SENIOR);
        assertThat(AgeCategory.SENIOR.minBirthDate()).isNull();
        assertThat(AgeCategory.SUB_8.maxBirthDate()).isEqualTo(LocalDate.now());
        assertThat(AgeCategory.SUB_8.minBirthDate()).isEqualTo(LocalDate.now().minusYears(8).plusDays(1));
    }

    @Test
    void payloadsAreTolerant() {
        Map<String, Object> p = new HashMap<>();
        p.put("n", "12"); p.put("bad", "x"); p.put("blank", "  "); p.put("d", "2020-01-02"); p.put("num", 7.9);
        assertThat(Payloads.integer(p, "n")).isEqualTo(12);
        assertThat(Payloads.integer(p, "bad")).isNull();
        assertThat(Payloads.integer(p, "num")).isEqualTo(7);
        assertThat(Payloads.lng(p, "n")).isEqualTo(12L);
        assertThat(Payloads.lng(p, "bad")).isNull();
        assertThat(Payloads.lng(p, "num")).isEqualTo(7L);
        assertThat(Payloads.str(p, "blank")).isNull();
        assertThat(Payloads.str(p, "missing")).isNull();
        assertThat(Payloads.date(p, "d")).isEqualTo(LocalDate.of(2020, 1, 2));
        assertThat(Payloads.date(p, "bad")).isNull();
        assertThat(Payloads.date(p, "missing")).isNull();
        assertThat(Payloads.integer(p, "missing")).isNull();
        assertThat(Payloads.lng(p, "missing")).isNull();
    }
}
