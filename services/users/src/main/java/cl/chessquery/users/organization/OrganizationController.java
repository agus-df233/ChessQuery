package cl.chessquery.users.organization;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.users.organization.OrganizationDtos.Response;
import cl.chessquery.users.organization.OrganizationDtos.UpsertRequest;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.roster.RosterDtos.CreateRequest;
import cl.chessquery.users.roster.RosterDtos.ImportReport;
import cl.chessquery.users.roster.RosterDtos.ImportRequest;
import cl.chessquery.users.roster.RosterDtos.InviteView;
import cl.chessquery.users.roster.RosterDtos.TagsRequest;
import cl.chessquery.users.roster.RosterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Club del organizador y su roster. Crear el club es lo que convierte a un jugador en
 * organizador; el resto de rutas exigen serlo. Toda operación es sobre "mi" club.
 */
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizations;
    private final RosterService roster;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@CurrentUser UserPrincipal user, @Valid @RequestBody UpsertRequest req) {
        return organizations.create(user.playerId(), req);
    }

    @GetMapping("/me")
    public Response mine(@CurrentUser UserPrincipal user) {
        return organizations.mine(user.playerId());
    }

    @PutMapping("/me")
    public Response update(@CurrentUser UserPrincipal user, @Valid @RequestBody UpsertRequest req) {
        return organizations.update(requireOrganizer(user), req);
    }

    // ── Roster provisorio ────────────────────────────────────────────────────

    @GetMapping("/me/roster")
    public List<Profile> roster(@CurrentUser UserPrincipal user) {
        return roster.list(requireOrganizer(user));
    }

    @PostMapping("/me/roster")
    @ResponseStatus(HttpStatus.CREATED)
    public Profile addToRoster(@CurrentUser UserPrincipal user, @Valid @RequestBody CreateRequest req) {
        return roster.add(requireOrganizer(user), req);
    }

    /** Carga masiva: informe por fila (creado, duplicado o error), sin cortar en la primera. */
    @PostMapping("/me/roster/import")
    public ImportReport importRoster(@CurrentUser UserPrincipal user, @Valid @RequestBody ImportRequest req) {
        return roster.importAll(requireOrganizer(user), req.rows());
    }

    /** Invitación (enlace o QR) para que el jugador real reclame su perfil del roster. */
    @PostMapping("/me/roster/{playerId}/invite")
    public InviteView invite(@CurrentUser UserPrincipal user, @PathVariable Long playerId) {
        return roster.invite(requireOrganizer(user), playerId);
    }

    @PatchMapping("/me/roster/{playerId}/tags")
    public Profile updateTags(@CurrentUser UserPrincipal user, @PathVariable Long playerId,
                              @RequestBody TagsRequest req) {
        return roster.updateTags(requireOrganizer(user), playerId, req.tags());
    }

    @DeleteMapping("/me/roster/{playerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@CurrentUser UserPrincipal user, @PathVariable Long playerId) {
        roster.deactivate(requireOrganizer(user), playerId);
    }

    /** El rol ORGANIZER se deriva de tener club; sin club no hay roster. */
    private static long requireOrganizer(UserPrincipal user) {
        if (!user.isOrganizer()) {
            throw ApiException.forbidden("ORGANIZER_REQUIRED", "Primero crea tu club");
        }
        return user.playerId();
    }
}
