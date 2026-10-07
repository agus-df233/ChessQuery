package cl.chessquery.game.users;

import cl.chessquery.auth.AuthProperties;
import cl.chessquery.auth.InternalHttp;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.rating.PlatformRatings;
import cl.chessquery.common.rating.TimeControlCategory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Lo que game le pide a users por {@code /internal}: nombre público y rating de plataforma de un jugador. */
@Component
public class UsersClient {

    private final RestClient http;

    public UsersClient(AuthProperties props) {
        this.http = InternalHttp.usersClient(props);
    }

    /** Subconjunto de {@code PlayerDtos.Summary} de users. */
    public record PlayerSummary(Long id, String firstName, String publicLastName, String currentTitle,
                                PlatformRatings platform, Integer eloNational, Integer eloFideStandard, boolean hasAccount) {

        public String publicName() {
            return (firstName + " " + (publicLastName == null ? "" : publicLastName)).trim();
        }

        /** ELO ChessQuery del ritmo, o null si nunca jugó ese ritmo por rating (entonces su K es la de provisional). */
        public Integer platformRating(TimeControlCategory category) {
            return platform == null ? null : platform.of(category);
        }

        /** Rating con que empieza a jugar un ritmo: ChessQuery de ese ritmo → nacional → FIDE → 1500. */
        public int startingRating(TimeControlCategory category) {
            Integer own = platformRating(category);
            if (own != null) return own;
            if (eloNational != null) return eloNational;
            return eloFideStandard != null ? eloFideStandard : 1500;
        }
    }

    public PlayerSummary player(long id) {
        List<PlayerSummary> found = http.get().uri("/internal/players?ids={id}", id).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        if (found == null || found.isEmpty()) {
            throw ApiException.notFound("PLAYER_NOT_FOUND", "Jugador " + id + " no encontrado");
        }
        return found.get(0);
    }
}
