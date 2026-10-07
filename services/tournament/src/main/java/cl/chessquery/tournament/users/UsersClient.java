package cl.chessquery.tournament.users;

import cl.chessquery.auth.AuthProperties;
import cl.chessquery.auth.InternalHttp;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.rating.PlatformRatings;
import cl.chessquery.common.rating.TimeControlCategory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Lo que tournament le pide a users por {@code /internal} (con {@code X-Internal-Token}). */
@Component
public class UsersClient {

    private final RestClient http;

    public UsersClient(AuthProperties props) {
        this.http = InternalHttp.usersClient(props);
    }

    /** Resumen de users ({@code PlayerDtos.Summary}); {@code publicLastName} ya abrevia a los menores. */
    public record PlayerSummary(Long id, String firstName, String lastName, String publicLastName, String currentTitle,
                                String clubName, Integer eloNational, Integer eloFideStandard, PlatformRatings platform,
                                String fideId, String federationId, Integer birthYear, String gender,
                                boolean provisional, Long createdByOrganizerId, boolean hasAccount) {

        /** ELO ChessQuery del ritmo, o null si nunca jugó ese ritmo por rating (entonces su K es la de provisional). */
        public Integer platformRating(TimeControlCategory category) {
            return platform == null ? null : platform.of(category);
        }

        /** Rating para sembrar un torneo de ese ritmo: ChessQuery del ritmo → nacional → FIDE → 1500. */
        public int seedRating(TimeControlCategory category) {
            Integer own = platformRating(category);
            if (own != null) return own;
            if (eloNational != null) return eloNational;
            return eloFideStandard != null ? eloFideStandard : 1500;
        }
    }

    public record PlanInfo(Long organizationId, String plan, int maxActiveTournaments) {}

    public List<PlayerSummary> players(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        String csv = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<PlayerSummary> found = http.get().uri("/internal/players?ids={ids}", csv).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return found == null ? List.of() : found;
    }

    public PlayerSummary player(long id) {
        return players(List.of(id)).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "Jugador " + id + " no encontrado"));
    }

    /**
     * Club y plan del organizador, consultado en el momento: la identidad en caché (5 min) puede no saber aún que
     * el jugador acaba de crear su club. Sin club, {@code organizationId} viene null.
     */
    public PlanInfo planOf(long ownerPlayerId) {
        return http.get().uri("/internal/organizations/by-owner/{id}/plan", ownerPlayerId).retrieve().body(PlanInfo.class);
    }
}
