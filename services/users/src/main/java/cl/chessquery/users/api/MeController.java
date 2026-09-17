package cl.chessquery.users.api;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/users")
public class MeController {

    public record MeResponse(long playerId, String email, Long organizationId, boolean organizer, Set<String> roles) {}

    /** Quién soy según la plataforma (no según el IdP): id interno, club propio, roles. */
    @GetMapping("/me")
    public MeResponse me(@CurrentUser UserPrincipal user) {
        return new MeResponse(user.playerId(), user.email(), user.organizationId(), user.isOrganizer(), user.roles());
    }
}
