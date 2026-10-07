package cl.chessquery.game.room;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoomCodesTest {

    @Test
    void generaCodigosDeSeisCaracteresSinAmbiguos() {
        RoomCodes codes = new RoomCodes(new Random(42));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = codes.next();
            assertThat(code).hasSize(6).doesNotContain("0", "O", "1", "I", "L");
            assertThat(RoomCodes.normalize(code)).isEqualTo(code);
            seen.add(code);
        }
        assertThat(seen).hasSizeGreaterThan(995); // prácticamente sin repetidos
        assertThat(new RoomCodes().next()).matches("[" + RoomCodes.ALPHABET + "]{6}");
    }

    /** Lo que escribe un alumno: minúsculas, espacios y guiones se aceptan; lo demás no puede ser un código. */
    @Test
    void normalizaLoQueEscribeElJugador() {
        assertThat(RoomCodes.normalize("ab3-k9q")).isEqualTo("AB3K9Q");
        assertThat(RoomCodes.normalize(" AB3 K9Q ")).isEqualTo("AB3K9Q");
        assertThat(RoomCodes.normalize(null)).isNull();
        assertThat(RoomCodes.normalize("AB3K9")).isNull();      // 5
        assertThat(RoomCodes.normalize("AB3K9QX")).isNull();    // 7
        assertThat(RoomCodes.normalize("AB0K9Q")).isNull();     // 0 no está en el alfabeto
        assertThat(RoomCodes.normalize("AB3K9'")).isNull();
    }
}
