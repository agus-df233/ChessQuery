package cl.chessquery.users.rating;

import cl.chessquery.users.catalog.Club;
import cl.chessquery.users.catalog.ClubRepository;
import cl.chessquery.common.events.Payloads;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.player.PlayerTitle;
import cl.chessquery.users.player.PlayerTitleRepository;
import cl.chessquery.users.privacy.DataSuppressionRepository;
import cl.chessquery.users.privacy.IdentifierHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Fuentes federadas (FIDE y la Federación nacional). Reglas, en orden:
 * <ol>
 *   <li><b>Supresión:</b> si alguno de sus identificadores pidió supresión u oposición, se omite.</li>
 *   <li><b>Identidad solo por identificadores</b> (id federativo → FIDE id → hash del RUT). Nunca por nombre:
 *       los homónimos quedan como filas separadas y el titular las reclama ("¿eres tú?").</li>
 *   <li><b>Minimización:</b> de terceros se guarda el año de nacimiento y el hash del RUT. El ETL de la
 *       Federación ya envía {@code rutHash}; si una fuente enviara {@code rut}, se hashea acá y se descarta.</li>
 *   <li>Completa datos vacíos sin pisar los curados a mano; los ELO y el título sí se actualizan.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FederatedRatingSource implements RatingSource {

    private static final Map<RatingType, String> ELO_FIELDS = Map.of(
            RatingType.NATIONAL, "eloNational", RatingType.FIDE_STANDARD, "eloFideStandard",
            RatingType.FIDE_RAPID, "eloFideRapid", RatingType.FIDE_BLITZ, "eloFideBlitz");

    private final PlayerRepository players;
    private final ClubRepository clubs;
    private final PlayerTitleRepository titles;
    private final RatingService ratings;
    private final IdentifierHasher hasher;
    private final DataSuppressionRepository suppressions;

    @Override
    public Set<String> sources() {
        return Set.of("FIDE", "FEDERACION", "AJEFECH");
    }

    @Override
    public boolean apply(Map<String, Object> p, String source, Instant at) {
        String firstName = Payloads.str(p, "firstName");
        String lastName = Payloads.str(p, "lastName");
        if (firstName == null || lastName == null) return false;

        Ids ids = Ids.from(p, hasher);
        if (isSuppressed(ids)) {
            log.debug("rating.updated: identificador suprimido, se omite");
            return false;
        }
        Player player = findByIds(ids).orElseGet(() -> create(firstName, lastName));
        if (player == null) return false;

        completeMissingData(player, ids, p);
        updateTitle(player, Payloads.str(p, "title"), Payloads.str(p, "period"), source);
        ELO_FIELDS.forEach((type, field) -> ratings.apply(player, type, Payloads.integer(p, field), at, source));
        ratings.markEnriched(player, source);
        return true;
    }

    /** Identificadores del payload; el RUT llega hasheado (o se hashea acá si viniera en claro). */
    private record Ids(String federationId, String fideId, String rutHash) {
        static Ids from(Map<String, Object> p, IdentifierHasher hasher) {
            String rutHash = Optional.ofNullable(Payloads.str(p, "rutHash")).orElseGet(() -> hasher.rut(Payloads.str(p, "rut")));
            return new Ids(Payloads.str(p, "federationId"), Payloads.str(p, "fideId"), rutHash);
        }
    }

    private boolean isSuppressed(Ids ids) {
        return suppressions.anySuppressed(Arrays.asList(
                hasher.federationId(ids.federationId()), hasher.fideId(ids.fideId()), ids.rutHash()));
    }

    private Optional<Player> findByIds(Ids ids) {
        Optional<Player> found = Optional.ofNullable(ids.federationId()).flatMap(players::findByFederationId);
        if (found.isEmpty() && ids.fideId() != null) found = players.findByFideId(ids.fideId());
        if (found.isEmpty() && ids.rutHash() != null) found = players.findByRutHash(ids.rutHash());
        return found;
    }

    /**
     * Solo rellena lo que está vacío: lo que el titular o un organizador cargó a mano manda. Un identificador que
     * ya pertenece a otro jugador no se copia (índice único): se registra el conflicto para revisión y el resto de
     * la fila se aplica. Así una ficha inconsistente no hace fallar el lote completo.
     */
    private void completeMissingData(Player player, Ids ids, Map<String, Object> p) {
        fillIdentifier(player, "federationId", player.getFederationId(), ids.federationId(),
                players::findByFederationId, player::setFederationId);
        fillIdentifier(player, "fideId", player.getFideId(), ids.fideId(), players::findByFideId, player::setFideId);
        fillIdentifier(player, "rutHash", player.getRutHash(), ids.rutHash(), players::findByRutHash, player::setRutHash);
        if (player.getBirthYear() == null) player.setBirthYear(birthYear(p));
        if (player.getSourceUrl() == null) player.setSourceUrl(Payloads.str(p, "sourceUrl"));
        Optional.ofNullable(Payloads.str(p, "period")).ifPresent(player::setSourcePeriod);
        String clubName = Payloads.str(p, "clubName");
        if (clubName != null && player.getClub() == null) player.setClub(findOrCreateClub(clubName));
    }

    /** Asigna el identificador si el jugador no lo tiene y ningún otro jugador lo tiene (si no, registra el conflicto). */
    private void fillIdentifier(Player player, String field, String current, String value,
                                Function<String, Optional<Player>> holderOf, Consumer<String> setter) {
        if (current != null || value == null) return;
        Optional<Player> holder = holderOf.apply(value);
        if (holder.isEmpty() || holder.get().getId().equals(player.getId())) {
            setter.accept(value);
            return;
        }
        log.warn("rating.updated: conflicto de identidad, {} ya pertenece al jugador {}; no se asigna al {}",
                field, holder.get().getId(), player.getId());
    }

    /** Título vigente (GM, IM, ...): cierra el anterior si cambió. Valores desconocidos se ignoran. */
    private void updateTitle(Player player, String raw, String period, String source) {
        Optional<PlayerTitle.Title> title = parseTitle(raw);
        if (title.isEmpty() || player.getId() == null) return;
        var current = titles.findFirstByPlayerIdAndCurrentTrue(player.getId());
        if (current.map(t -> t.getTitle() == title.get()).orElse(false)) return;
        current.ifPresent(t -> { t.close(); titles.save(t); });
        LocalDate since = period == null ? LocalDate.now() : LocalDate.parse(period + "-01");
        titles.save(PlayerTitle.current(player.getId(), title.get(), since, source));
    }

    private static Optional<PlayerTitle.Title> parseTitle(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(PlayerTitle.Title.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /** {@code birthYear} explícito (FIDE y la Federación publican solo lo necesario) o el año de {@code birthDate}. */
    private static Integer birthYear(Map<String, Object> p) {
        Integer year = Payloads.integer(p, "birthYear");
        if (year != null) return year;
        var date = Payloads.date(p, "birthDate");
        return date == null ? null : date.getYear();
    }

    private Player create(String firstName, String lastName) {
        try {
            return players.saveAndFlush(Player.builder().firstName(firstName).lastName(lastName).build());
        } catch (DataIntegrityViolationException race) {
            log.debug("Carrera creando jugador federado {} {}", firstName, lastName);
            return null;
        }
    }

    private Club findOrCreateClub(String name) {
        return clubs.findFirstByNameIgnoreCase(name).orElseGet(() -> clubs.save(new Club(name)));
    }
}
