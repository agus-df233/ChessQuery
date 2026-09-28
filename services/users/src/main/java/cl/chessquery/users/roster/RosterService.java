package cl.chessquery.users.roster;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.catalog.ClubRepository;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.organization.OrganizationService;
import cl.chessquery.users.player.Emails;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.privacy.IdentifierHasher;
import cl.chessquery.users.roster.RosterDtos.CreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Roster de jugadores provisorios del club: el organizador carga a su gente antes de que tenga
 * cuenta y los inscribe a torneos. Cuando uno de ellos entra con el mismo email, reclama su fila
 * (ver IdentityService). El tamaño del roster activo lo limita el plan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RosterService {

    private final PlayerRepository players;
    private final ClubRepository clubs;
    private final OrganizationService organizations;
    private final EventPublisher events;
    private final IdentifierHasher hasher;

    @Transactional
    public Profile add(Long organizerId, CreateRequest req) {
        int max = organizations.planOf(organizerId).maxRosterPlayers();
        if (organizations.rosterCount(organizerId) >= max) {
            throw ApiException.conflict("PLAN_LIMIT_REACHED",
                    "Alcanzaste el límite de tu plan (" + max + " jugadores en el roster)");
        }
        String email = Emails.normalize(req.email());
        if (email != null && players.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_TAKEN", "Ya existe un jugador con ese email");
        }
        String rut = req.rut() == null || req.rut().isBlank() ? null : req.rut().trim();
        String rutHash = hasher.rut(rut);
        if (rutHash != null && players.findByRutHash(rutHash).isPresent()) {
            throw ApiException.conflict("RUT_TAKEN", "Ya existe un jugador con ese RUT");
        }
        Player p = Player.builder()
                .firstName(req.firstName().trim()).lastName(req.lastName().trim())
                .email(email).rut(rut).rutHash(rutHash)
                .eloNational(positiveOrNull(req.eloNational()))
                .eloFideStandard(positiveOrNull(req.eloFideStandard()))
                .club(req.clubId() == null ? null : clubs.findById(req.clubId())
                        .orElseThrow(() -> ApiException.notFound("CLUB_NOT_FOUND", "Club no encontrado")))
                .provisional(true).createdByOrganizerId(organizerId)
                .build();
        p.setTagList(req.tags());
        p = players.save(p);

        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", p.getId());
        payload.put("organizerId", organizerId);
        payload.put("email", email == null ? "" : email);
        events.publish(UsersEvents.PROVISIONAL_CREATED, payload);
        log.info("Provisorio {} creado por organizador {}", p.getId(), organizerId);
        return Profile.of(p, null);
    }

    @Transactional(readOnly = true)
    public List<Profile> list(Long organizerId) {
        return players.findByCreatedByOrganizerIdAndProvisionalTrueOrderByLastNameAscFirstNameAsc(organizerId)
                .stream().map(p -> Profile.of(p, null)).toList();
    }

    @Transactional
    public Profile updateTags(Long organizerId, Long playerId, List<String> tags) {
        Player p = requireOwned(organizerId, playerId);
        p.setTagList(tags);
        return Profile.of(players.save(p), null);
    }

    /** Baja lógica: el jugador puede tener historial de torneos, nunca se borra. */
    @Transactional
    public void deactivate(Long organizerId, Long playerId) {
        Player p = requireOwned(organizerId, playerId);
        p.setActive(false);
        players.save(p);
    }

    private Player requireOwned(Long organizerId, Long playerId) {
        Player p = players.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "Jugador no encontrado"));
        if (!p.isProvisional() || !organizerId.equals(p.getCreatedByOrganizerId())) {
            throw ApiException.forbidden("NOT_YOUR_PROVISIONAL", "Ese jugador no pertenece a tu roster");
        }
        return p;
    }

    private static Integer positiveOrNull(Integer v) {
        return v == null || v <= 0 ? null : v;
    }
}
