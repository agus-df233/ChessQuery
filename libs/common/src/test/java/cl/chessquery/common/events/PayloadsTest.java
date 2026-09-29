package cl.chessquery.common.events;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadsTest {

    @Test
    void leeValoresTolerandoTiposYBasura() {
        Map<String, Object> p = new HashMap<>(Map.of("s", " hola ", "blank", "  ", "n", 7, "ns", "42", "bad", "x",
                "d", "2026-10-03", "big", 9_000_000_000L));
        assertThat(Payloads.str(p, "s")).isEqualTo("hola");
        assertThat(Payloads.str(p, "blank")).isNull();
        assertThat(Payloads.str(p, "none")).isNull();
        assertThat(Payloads.integer(p, "n")).isEqualTo(7);
        assertThat(Payloads.integer(p, "ns")).isEqualTo(42);
        assertThat(Payloads.integer(p, "bad")).isNull();
        assertThat(Payloads.integer(p, "none")).isNull();
        assertThat(Payloads.lng(p, "big")).isEqualTo(9_000_000_000L);
        assertThat(Payloads.lng(p, "ns")).isEqualTo(42L);
        assertThat(Payloads.lng(p, "bad")).isNull();
        assertThat(Payloads.lng(p, "none")).isNull();
        assertThat(Payloads.date(p, "d")).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(Payloads.date(p, "bad")).isNull();
        assertThat(Payloads.date(p, "none")).isNull();
    }
}
