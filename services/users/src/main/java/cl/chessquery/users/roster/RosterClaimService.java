package cl.chessquery.users.roster;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.organization.OrganizationRepository;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.player.PlayerTitleRepository;
import cl.chessquery.users.privacy.PublicNames;
import cl.chessquery.users.rating.RatingHistoryRepository;
import cl.chessquery.users.rating.RatingType;
import cl.chessquery.users.roster.RosterDtos.ClaimPreview;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/**
 * El jugador real reclama el perfil que su club le creó en el roster, con la invitación del organizador (enlace o QR).
 * Funciona aunque su cuenta tenga otro email (o el roster no lo tuviera): la invitación es la prueba de que el club lo
 * reconoce. El perfil del roster se une a la cuenta: ratings que la cuenta no tenga, historial, títulos y club, y sus
 * torneos pasan a la cuenta (evento {@code player.merged}: tournament reescribe inscripciones y mesas).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RosterClaimService {

    private final PlayerRepository players;
    private final OrganizationRepository organizations;
    private final RatingHistoryRepository history;
    private final PlayerTitleRepository titles;
    private final EventPublisher events;
    private final Clock clock;

    /** Lo que se muestra antes de aceptar: el nombre público y el club que lo cargó (nada más). */
    @Transactional(readOnly = true)
    public ClaimPreview preview(String token) {
        Player target = requireClaimable(token);
        String club = organizations.findByOwnerPlayerId(target.getCreatedByOrganizerId())
                .map(o -> o.getName()).orElse(null);
        return new ClaimPreview(target.getFirstName(), PublicNames.lastName(target), club);
    }

    @Transactional
    public Profile claim(Long playerId, String token) {
        Player target = requireClaimable(token);
        Player me = players.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "Jugador " + playerId + " no encontrado"));
        if (target.getId().equals(me.getId())) throw notClaimable();
        merge(target, me);
        events.publish(UsersEvents.PLAYER_MERGED, Map.of("fromPlayerId", target.getId(), "intoPlayerId", me.getId()));
        log.info("Jugador {} reclamó el perfil {} del roster del organizador {}", me.getId(), target.getId(),
                target.getCreatedByOrganizerId());
        return Profile.of(me, titles.currentTitleOf(me.getId()));
    }

    private Player requireClaimable(String token) {
        Player target = players.findByClaimToken(token == null ? "" : token.trim())
                .orElseThrow(() -> ApiException.notFound("INVITE_NOT_FOUND", "Esa invitación no existe o ya se usó"));
        if (RosterService.expired(target, clock.instant())) {
            throw ApiException.conflict("INVITE_EXPIRED", "Esa invitación venció: pídele una nueva a tu club");
        }
        if (!target.isProvisional() || target.hasAccount() || !target.isActive()) throw notClaimable();
        return target;
    }

    private static ApiException notClaimable() {
        return ApiException.conflict("NOT_CLAIMABLE", "Ese perfil no se puede reclamar");
    }

    /** El perfil del roster cede lo que la cuenta no tenga y queda inactivo (no se borra: integridad del historial). */
    private void merge(Player from, Player into) {
        String rut = from.getRut(), rutHash = from.getRutHash(), fed = from.getFederationId(), fide = from.getFideId();
        from.setRut(null);
        from.setRutHash(null);
        from.setFederationId(null);
        from.setFideId(null);
        from.setEmail(null);
        from.setClaimToken(null);
        from.setActive(false);
        players.saveAndFlush(from); // libera los índices únicos antes de asignarlos a la cuenta

        if (into.getRutHash() == null && rutHash != null) { into.setRutHash(rutHash); into.setRut(rut); }
        if (into.getFederationId() == null) into.setFederationId(fed);
        if (into.getFideId() == null) into.setFideId(fide);
        if (into.getBirthYear() == null) into.setBirthYear(from.getBirthYear());
        if (into.getClub() == null) into.setClub(from.getClub());
        for (RatingType type : RatingType.values()) {
            if (into.rating(type) == null && from.rating(type) != null) into.setRating(type, from.rating(type));
        }
        players.save(into);
        history.reassign(from.getId(), into.getId());
        titles.reassign(from.getId(), into.getId());
    }
}
