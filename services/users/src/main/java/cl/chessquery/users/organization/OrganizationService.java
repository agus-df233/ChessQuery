package cl.chessquery.users.organization;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.users.organization.OrganizationDtos.PlanInfo;
import cl.chessquery.users.organization.OrganizationDtos.Response;
import cl.chessquery.users.organization.OrganizationDtos.UpsertRequest;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Club del organizador. La creación es explícita ("crear mi club") y convierte al jugador en
 * organizador. No hay auto-provisión en GET: el rol debe ser una decisión del usuario.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final PlayerRepository players;

    @Transactional
    public Response create(Long ownerId, UpsertRequest req) {
        if (organizations.findByOwnerPlayerId(ownerId).isPresent()) {
            throw ApiException.conflict("ORGANIZATION_EXISTS", "Ya tienes un club");
        }
        Organization org = new Organization(ownerId, req.name().trim());
        applyDetails(org, req);
        try {
            org = organizations.saveAndFlush(org);
        } catch (DataIntegrityViolationException race) {
            throw ApiException.conflict("ORGANIZATION_EXISTS", "Ya tienes un club");
        }
        log.info("Club {} creado por jugador {}", org.getId(), ownerId);
        return Response.of(org, 0);
    }

    @Transactional(readOnly = true)
    public Response mine(Long ownerId) {
        Organization org = require(ownerId);
        return Response.of(org, rosterCount(ownerId));
    }

    @Transactional
    public Response update(Long ownerId, UpsertRequest req) {
        Organization org = require(ownerId);
        org.setName(req.name().trim());
        applyDetails(org, req);
        organizations.save(org);
        return Response.of(org, rosterCount(ownerId));
    }

    /** Plan vigente del organizador; FREE (sin id) si aún no creó club. */
    @Transactional(readOnly = true)
    public PlanInfo planOf(Long ownerId) {
        return organizations.findByOwnerPlayerId(ownerId)
                .map(o -> new PlanInfo(o.getId(), o.getPlan().name(), o.getPlan().maxRosterPlayers, o.getPlan().maxActiveTournaments))
                .orElseGet(() -> new PlanInfo(null, Organization.Plan.FREE.name(),
                        Organization.Plan.FREE.maxRosterPlayers, Organization.Plan.FREE.maxActiveTournaments));
    }

    Organization require(Long ownerId) {
        return organizations.findByOwnerPlayerId(ownerId)
                .orElseThrow(() -> ApiException.notFound("ORGANIZATION_NOT_FOUND", "Todavía no creas tu club"));
    }

    /** Jugadores provisorios activos del organizador (lo que cuenta contra el plan). */
    public int rosterCount(Long ownerId) {
        return (int) players.countByCreatedByOrganizerIdAndProvisionalTrueAndActiveTrue(ownerId);
    }

    private static void applyDetails(Organization org, UpsertRequest req) {
        org.setCity(blankToNull(req.city()));
        org.setDescription(blankToNull(req.description()));
        org.setLogoUrl(blankToNull(req.logoUrl()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
