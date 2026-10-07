package cl.chessquery.users.roster;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.roster.RosterDtos.ClaimPreview;
import cl.chessquery.users.roster.RosterDtos.ClaimRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** El jugador abre la invitación de su club (con su cuenta), ve de qué perfil se trata y lo une al suyo. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class RosterClaimController {

    private final RosterClaimService claims;

    @GetMapping("/claim-invite/{token}")
    public ClaimPreview preview(@CurrentUser UserPrincipal me, @PathVariable String token) {
        return claims.preview(token);
    }

    @PostMapping("/me/claim-invite")
    public Profile claim(@CurrentUser UserPrincipal me, @Valid @RequestBody ClaimRequest req) {
        return claims.claim(me.playerId(), req.token());
    }
}
