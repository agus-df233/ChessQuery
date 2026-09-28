package cl.chessquery.users.ranking;

import cl.chessquery.users.events.Payloads;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgeCategoryTest {

    @Test
    void categoriesUseTheAgeReachedThisYear() {
        int year = Year.now().getValue();
        assertThat(AgeCategory.fromBirthYear(null)).isEqualTo(AgeCategory.ADULTO);
        assertThat(AgeCategory.fromBirthYear(year - 8)).isEqualTo(AgeCategory.SUB_8);   // cumple 8 este año
        assertThat(AgeCategory.fromBirthYear(year - 9)).isEqualTo(AgeCategory.SUB_10);
        assertThat(AgeCategory.fromBirthYear(year - 70)).isEqualTo(AgeCategory.SENIOR);
        assertThat(AgeCategory.fromBirthDate(LocalDate.of(year - 11, 12, 31))).isEqualTo(AgeCategory.SUB_12);
        assertThat(AgeCategory.fromBirthDate(null)).isEqualTo(AgeCategory.ADULTO);
        assertThat(AgeCategory.SENIOR.minBirthYear()).isNull();
        assertThat(AgeCategory.SUB_8.maxBirthYear()).isEqualTo(year);
        assertThat(AgeCategory.SUB_8.minBirthYear()).isEqualTo(year - 8);
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
