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
import cl.chessquery.users.roster.RosterDtos.ImportReport;
import cl.chessquery.users.roster.RosterDtos.ImportRow;
import cl.chessquery.users.roster.RosterDtos.InviteView;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
    private final Validator validator;
    private final Clock clock;

    static final Duration INVITE_TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public Profile add(Long organizerId, CreateRequest req) {
        ensureWithinPlan(organizerId);
        String email = Emails.normalize(req.email());
        String rut = req.rut() == null || req.rut().isBlank() ? null : req.rut().trim();
        String rutHash = hasher.rut(rut);
        ensureUniqueIdentity(email, rutHash);
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

    /** El plan (FREE/PRO) limita cuántos jugadores activos puede tener el roster. */
    private void ensureWithinPlan(Long organizerId) {
        int max = organizations.planOf(organizerId).maxRosterPlayers();
        if (organizations.rosterCount(organizerId) >= max) {
            throw ApiException.conflict("PLAN_LIMIT_REACHED",
                    "Alcanzaste el límite de tu plan (" + max + " jugadores en el roster)");
        }
    }

    /** Email y RUT identifican a una persona: no se puede cargar a alguien que ya existe en la plataforma. */
    private void ensureUniqueIdentity(String email, String rutHash) {
        if (email != null && players.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_TAKEN", "Ya existe un jugador con ese email");
        }
        if (rutHash != null && players.findByRutHash(rutHash).isPresent()) {
            throw ApiException.conflict("RUT_TAKEN", "Ya existe un jugador con ese RUT");
        }
    }

    /**
     * Carga masiva: cada fila se crea por separado y el informe dice qué pasó con cada una (creada, duplicada por
     * email o RUT, o con error), sin cortar en la primera. El límite del plan se aplica fila a fila.
     */
    @Transactional
    public ImportReport importAll(Long organizerId, List<CreateRequest> rows) {
        List<ImportRow> report = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) report.add(importRow(organizerId, i + 1, rows.get(i)));
        return new ImportReport(count(report, "CREATED"), count(report, "DUPLICATE"), count(report, "ERROR"), report);
    }

    private ImportRow importRow(Long organizerId, int row, CreateRequest req) {
        var violations = validator.validate(req);
        if (!violations.isEmpty()) {
            var v = violations.iterator().next();
            return new ImportRow(row, "ERROR", null, "INVALID_ROW", v.getPropertyPath() + ": " + v.getMessage());
        }
        try {
            return new ImportRow(row, "CREATED", add(organizerId, req).id(), null, null);
        } catch (ApiException e) {
            boolean duplicate = "EMAIL_TAKEN".equals(e.getError()) || "RUT_TAKEN".equals(e.getError());
            return new ImportRow(row, duplicate ? "DUPLICATE" : "ERROR", null, e.getError(), e.getMessage());
        }
    }

    private static int count(List<ImportRow> rows, String outcome) {
        return (int) rows.stream().filter(r -> r.outcome().equals(outcome)).count();
    }

    /**
     * Invitación para que el jugador real reclame este perfil del roster: un token aleatorio (128 bits) que vence a
     * los {@link #INVITE_TTL}. Pedirla de nuevo antes de que venza devuelve la misma.
     */
    @Transactional
    public InviteView invite(Long organizerId, Long playerId) {
        Player p = requireOwned(organizerId, playerId);
        if (p.hasAccount()) throw ApiException.conflict("ALREADY_CLAIMED", "Ese jugador ya tiene cuenta");
        Instant now = clock.instant();
        if (p.getClaimToken() == null || expired(p, now)) {
            p.setClaimToken(newToken());
            p.setClaimTokenCreatedAt(now);
            players.save(p);
        }
        return new InviteView(p.getId(), p.getClaimToken(), p.getClaimTokenCreatedAt().plus(INVITE_TTL));
    }

    static boolean expired(Player p, Instant now) {
        return p.getClaimTokenCreatedAt() == null || !now.isBefore(p.getClaimTokenCreatedAt().plus(INVITE_TTL));
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
