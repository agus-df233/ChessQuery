package cl.chessquery.game.users;

import cl.chessquery.common.rating.TimeControlCategory;
import cl.chessquery.common.rating.PlatformRatings;
import cl.chessquery.game.users.UsersClient.PlayerSummary;
import org.junit.jupiter.api.Test;

import static cl.chessquery.common.rating.TimeControlCategory.*;
import static org.assertj.core.api.Assertions.assertThat;

class PlayerSummaryTest {

    static PlayerSummary with(PlatformRatings platform, Integer national, Integer fide) {
        return new PlayerSummary(1L, "Ana", "Soto", null, platform, national, fide, true);
    }

    @Test
    void cadaRitmoLeeSuPropioElo() {
        PlatformRatings p = new PlatformRatings(1100, 1200, 1300, 1400);
        assertThat(p.of(BULLET)).isEqualTo(1100);
        assertThat(p.of(BLITZ)).isEqualTo(1200);
        assertThat(p.of(RAPID)).isEqualTo(1300);
        assertThat(p.of(CLASSICAL)).isEqualTo(1400);
    }

    /** Sin ELO del ritmo se parte del nacional, luego del FIDE y por último de 1500 (y se juega como provisional). */
    @Test
    void ratingInicialCaeEnCadenaSiNoJuegaEseRitmo() {
        PlayerSummary soloBlitz = with(new PlatformRatings(null, 1700, null, null), 1800, 1900);
        assertThat(soloBlitz.startingRating(BLITZ)).isEqualTo(1700);
        assertThat(soloBlitz.platformRating(BLITZ)).isEqualTo(1700);
        assertThat(soloBlitz.startingRating(RAPID)).isEqualTo(1800);
        assertThat(soloBlitz.platformRating(RAPID)).isNull();
        assertThat(with(null, null, 1900).startingRating(CLASSICAL)).isEqualTo(1900);
        assertThat(with(null, null, null).startingRating(BULLET)).isEqualTo(1500);
        assertThat(with(null, null, null).platformRating(BULLET)).isNull();
    }

    @Test
    void todosLosRitmosTienenSuTipoDeRating() {
        for (TimeControlCategory c : TimeControlCategory.values()) {
            assertThat(c.ratingType()).startsWith("PLATFORM_");
        }
    }
}
