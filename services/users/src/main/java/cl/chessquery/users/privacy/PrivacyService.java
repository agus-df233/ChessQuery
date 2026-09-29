package cl.chessquery.users.privacy;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.friends.Friendship;
import cl.chessquery.users.friends.FriendshipRepository;
import cl.chessquery.users.organization.OrganizationRepository;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.player.PlayerDtos.PublicProfile;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.player.PlayerTitleRepository;
import cl.chessquery.users.rating.RatingHistoryRepository;
import cl.chessquery.users.rating.RatingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Derechos del titular (Ley 19.628 / 21.719): acceso y portabilidad ({@link #export}), supresión
 * ({@link #erase}) y el reclamo asistido de filas federadas homónimas ({@link #claimSuggestions}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyService {

    private final PlayerRepository players;
    private final PlayerTitleRepository titles;
    private final RatingHistoryRepository history;
    private final FriendshipRepository friendships;
    private final OrganizationRepository organizations;
    private final DataSuppressionRepository suppressions;
    private final IdentifierHasher hasher;
    private final EventPublisher events;

    public record RatingRecord(RatingType type, int rating, Integer previous, Short delta, Instant recordedAt,
                               String source) {}

    public record OrganizationRecord(Long id, String name, String city, String plan, Instant createdAt) {}

    /** Todo lo que la plataforma guarda del titular, en un JSON portable. */
    public record Export(Instant generatedAt, Profile profile, List<RatingRecord> ratingHistory,
                         List<Long> friendIds, OrganizationRecord organization) {}

    @Transactional(readOnly = true)
    public Export export(Long playerId) {
        Player p = require(playerId);
        List<RatingRecord> ratings = history.findByPlayerIdOrderByRecordedAtAsc(playerId).stream()
                .map(h -> new RatingRecord(h.getRatingType(), h.getRatingValue(), h.getRatingPrevValue(), h.getDelta(),
                        h.getRecordedAt(), h.getSource()))
                .toList();
        List<Long> friends = friendships.findByPlayerAndStatus(playerId, Friendship.Status.ACCEPTED).stream()
                .map(f -> f.getRequesterId().equals(playerId) ? f.getAddresseeId() : f.getRequesterId())
                .toList();
        OrganizationRecord org = organizations.findByOwnerPlayerId(playerId)
                .map(o -> new OrganizationRecord(o.getId(), o.getName(), o.getCity(), o.getPlan().name(), o.getCreatedAt()))
                .orElse(null);
        return new Export(Instant.now(), Profile.of(p, titles.currentTitleOf(playerId)), ratings, friends, org);
    }

    /**
     * Supresión: anonimiza la fila (se conserva el id porque torneos y partidas lo referencian),
     * borra amistades, registra los identificadores en la lista de supresión para que el ETL no
     * vuelva a importarlos y avisa a los demás servicios con {@code player.deleted}. La cuenta en el
     * IdP (Entra) se elimina aparte; si vuelve a entrar, se le provisiona un perfil nuevo y vacío.
     */
    @Transactional
    public void erase(Long playerId) {
        Player p = require(playerId);
        if (organizations.findByOwnerPlayerId(playerId).isPresent()) {
            throw ApiException.conflict("ORGANIZATION_OWNER",
                    "Eres dueño de un club: transfiérelo o elimínalo antes de borrar tu cuenta");
        }
        Stream.of(hasher.fideId(p.getFideId()), hasher.federationId(p.getFederationId()),
                        p.getRutHash() != null ? p.getRutHash() : hasher.rut(p.getRut()))
                .filter(Objects::nonNull)
                .filter(h -> !suppressions.existsById(h))
                .forEach(h -> suppressions.save(new DataSuppression(h, DataSuppression.Reason.ERASURE)));

        friendships.deleteAllOf(playerId);
        anonymize(p);
        players.save(p);
        events.publish(UsersEvents.PLAYER_DELETED, Map.of("playerId", playerId));
        log.info("Jugador {} suprimido a pedido del titular", playerId);
    }

    /** Filas federadas sin dueño con el mismo nombre: el titular decide si alguna es suya. */
    @Transactional(readOnly = true)
    public List<PublicProfile> claimSuggestions(Long playerId) {
        Player me = require(playerId);
        return players.findUnclaimedByFullName(me.fullName(), playerId).stream()
                .filter(c -> me.getBirthYear() == null || c.getBirthYear() == null
                        || me.getBirthYear().equals(c.getBirthYear()))
                .map(c -> PublicProfile.of(c, titles.currentTitleOf(c.getId())))
                .toList();
    }

    private static void anonymize(Player p) {
        p.setFirstName("Jugador");
        p.setLastName("eliminado");
        p.setDisplayName(null);
        p.setEmail(null);
        p.setRut(null);
        p.setRutHash(null);
        p.setBirthDate(null);
        p.setBirthYear(null);
        p.setGender(null);
        p.setCountry(null);
        p.setRegion(null);
        p.setClub(null);
        p.setFideId(null);
        p.setFederationId(null);
        p.setLichessUsername(null);
        p.setChesscomUsername(null);
        p.setExternalSubject(null);
        p.setTags(null);
        p.setSourceUrl(null);
        p.setSourcePeriod(null);
        p.setParentalConsentAt(null);
        p.setEnrichmentSource(null);
        p.setEnrichedAt(null);
        for (RatingType t : RatingType.values()) p.setRating(t, null); // sale de rankings y búsquedas
        p.setActive(false);
    }

    private Player require(Long id) {
        return players.findById(id)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "Jugador " + id + " no encontrado"));
    }
}
