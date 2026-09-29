package cl.chessquery.users.internal;

import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.users.friends.FriendshipService;
import cl.chessquery.users.identity.IdentityService;
import cl.chessquery.users.organization.OrganizationDtos.PlanInfo;
import cl.chessquery.users.organization.OrganizationService;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.player.PlayerDtos.Summary;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.player.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrato servicio→servicio, protegido por {@code X-Internal-Token} y nunca expuesto por el ALB.
 * Lo consumen la librería auth-starter (identidad), tournament y game (resúmenes, plan, amistad)
 * y el ETL (usernames vinculados).
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final IdentityService identity;
    private final PlayerService playerService;
    private final PlayerRepository players;
    private final OrganizationService organizations;
    private final FriendshipService friendships;

    // ── Identidad ────────────────────────────────────────────────────────────

    @GetMapping("/players/by-subject/{subject}")
    public ResolvedIdentity bySubject(@PathVariable String subject) {
        return identity.find(subject);
    }

    /** {@code issuer}/{@code emailVerified}: sin ellos el correo no se considera verificado (no adopta perfiles). */
    public record ProvisionRequest(@NotBlank String subject, String email, String firstName,
                                   String lastName, String displayName, String issuer, Boolean emailVerified) {}

    @PostMapping("/players/provision")
    @ResponseStatus(HttpStatus.CREATED)
    public ResolvedIdentity provision(@Valid @RequestBody ProvisionRequest req) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", req.email());
        claims.put("given_name", req.firstName());
        claims.put("family_name", req.lastName());
        claims.put("name", req.displayName());
        claims.put("iss", req.issuer());
        claims.put("email_verified", req.emailVerified());
        return identity.resolve(req.subject(), claims);
    }

    // ── Jugadores ────────────────────────────────────────────────────────────

    /** Resúmenes en lote: {@code ?ids=1,2,3}. */
    @GetMapping("/players")
    public List<Summary> summaries(@RequestParam List<Long> ids) {
        return playerService.summaries(ids);
    }

    @GetMapping("/players/{id}")
    public Profile profile(@PathVariable Long id) {
        return playerService.profile(id);
    }

    @GetMapping("/players/by-email")
    public Profile byEmail(@RequestParam String email) {
        return playerService.profileByEmail(email);
    }

    /** Usernames vinculados para el ETL (sincronización en lote). */
    public record ExternalUsernames(List<String> lichess, List<String> chesscom) {}

    @GetMapping("/players/external-usernames")
    public ExternalUsernames externalUsernames() {
        return new ExternalUsernames(players.findAllLichessUsernames(), players.findAllChesscomUsernames());
    }

    // ── Organización y amistad ───────────────────────────────────────────────

    /** Plan y límites del club de un organizador (tournament lo usa para limitar torneos activos). */
    @GetMapping("/organizations/by-owner/{playerId}/plan")
    public PlanInfo planOf(@PathVariable Long playerId) {
        return organizations.planOf(playerId);
    }

    /** ¿Son amigos? game lo usa para autorizar espectar una partida privada. */
    @GetMapping("/friends/are-friends")
    public Map<String, Boolean> areFriends(@RequestParam Long a, @RequestParam Long b) {
        return Map.of("friends", friendships.areFriends(a, b));
    }
}
