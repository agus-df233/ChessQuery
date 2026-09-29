package cl.chessquery.users.player;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.player.PlayerDtos.PublicProfile;
import cl.chessquery.users.player.PlayerDtos.SearchResult;
import cl.chessquery.users.player.PlayerDtos.UpdateProfileRequest;
import cl.chessquery.users.ranking.RankingService;
import cl.chessquery.users.rating.RatingHistory;
import cl.chessquery.users.rating.RatingService;
import cl.chessquery.users.rating.RatingType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * API del jugador. Todo lo que es "mío" cuelga de {@code /me} y toma la identidad del token
 * ({@link CurrentUser}); nunca de un id en la URL o en el body.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final PlayerService playerService;
    private final RatingService ratingService;
    private final RankingService rankingService;

    /** Quién soy para la plataforma: perfil completo + club propio + roles. */
    public record Me(Profile profile, Long organizationId, boolean organizer, Set<String> roles) {}

    @GetMapping("/me")
    public Me me(@CurrentUser UserPrincipal user) {
        return new Me(playerService.profile(user.playerId()), user.organizationId(), user.isOrganizer(), user.roles());
    }

    @PutMapping("/me/profile")
    public Profile updateMyProfile(@CurrentUser UserPrincipal user, @Valid @RequestBody UpdateProfileRequest req) {
        return playerService.updateProfile(user.playerId(), req);
    }

    /** Serie para el gráfico de progreso (por defecto ELO nacional, últimos 12 meses). */
    @GetMapping("/me/rating-history")
    public List<RatingHistory.Point> myRatingHistory(@CurrentUser UserPrincipal user,
                                                     @RequestParam(defaultValue = "NATIONAL") RatingType type,
                                                     @RequestParam(defaultValue = "12") int months) {
        return ratingService.series(user.playerId(), type, months);
    }

    /** Trae los ratings actuales de las cuentas externas vinculadas (Lichess, Chess.com). */
    @PostMapping("/me/external-ratings/sync")
    public Profile syncMyExternalRatings(@CurrentUser UserPrincipal user) {
        return playerService.syncExternalRatings(user.playerId());
    }

    /** Perfil de otro jugador: solo la proyección pública. */
    @GetMapping("/{id}/public-profile")
    public PublicProfile publicProfile(@PathVariable Long id) {
        return playerService.publicProfile(id);
    }

    @GetMapping("/{id}/rating-history")
    public List<RatingHistory.Point> ratingHistory(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "NATIONAL") RatingType type,
                                                   @RequestParam(defaultValue = "12") int months) {
        playerService.publicProfile(id); // 404 si no existe
        return ratingService.series(id, type, months);
    }

    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
        return playerService.search(q, limit);
    }

    @GetMapping("/ranking")
    public List<RankingService.Entry> ranking(@RequestParam(defaultValue = "NATIONAL") String type,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) String region,
                                              @RequestParam(defaultValue = "50") int limit) {
        return rankingService.ranking(type, category, region, limit);
    }
}
