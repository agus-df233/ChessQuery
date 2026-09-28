package cl.chessquery.users.rating;

import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.users.catalog.Club;
import cl.chessquery.users.catalog.ClubRepository;
import cl.chessquery.users.events.Payloads;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.player.PlayerTitle;
import cl.chessquery.users.player.PlayerTitleRepository;
import cl.chessquery.users.privacy.DataSuppressionRepository;
import cl.chessquery.users.privacy.IdentifierHasher;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consume {@code rating.updated} (lo emite el ETL tras sincronizar una fuente):
 * {@code { source: AJEFECH|LICHESS|CHESSCOM, players: [ {...} ] }}.
 *
 * <ul>
 *   <li><b>FIDE / AJEFECH</b> (federados): identifica al jugador <i>solo</i> por identificadores
 *       (federationId → fideId → hash del RUT); si no existe lo crea como fila federada sin cuenta.
 *       Nunca fusiona por nombre: los homónimos quedan como filas separadas y el titular las
 *       reclama ("¿eres tú?"). Minimización: de terceros guarda el año de nacimiento y el hash
 *       del RUT, no la fecha ni el RUT en claro. Salta a quien pidió supresión u oposición.
 *       Completa datos vacíos, nunca pisa los curados a mano; los ELO sí se actualizan.</li>
 *   <li><b>LICHESS / CHESSCOM</b>: solo enriquece a quien vinculó ese usuario; no crea jugadores
 *       (un username no es una identidad).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingUpdatedConsumer {

    private final IdempotentConsumer idempotent;
    private final PlayerRepository players;
    private final ClubRepository clubs;
    private final RatingService ratings;
    private final IdentifierHasher hasher;
    private final DataSuppressionRepository suppressions;
    private final PlayerTitleRepository titles;

    /** Cola SQS dedicada, suscrita al tópico con filter policy por eventType (docs/events.md). */
    @SqsListener("${chessquery.events.queues.rating}")
    public void onRatingUpdated(ChessEvent event) {
        if (!UsersEvents.RATING_UPDATED.equals(event.eventType())) return;
        idempotent.handle(event, this::apply);
    }

    /** Visible para pruebas; la idempotencia la aplica el listener. */
    @Transactional
    public void apply(ChessEvent event) {
        String source = Payloads.str(event.payload(), "source");
        if (!(event.payload().get("players") instanceof List<?> list) || source == null) {
            log.info("rating.updated sin fuente o sin jugadores; nada que hacer");
            return;
        }
        int touched = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) raw;
                try {
                    if (applyOne(p, source.toUpperCase(), event.timestamp())) touched++;
                } catch (RuntimeException e) {
                    log.warn("rating.updated: jugador {} ignorado: {}", p.get("federationId"), e.getMessage());
                }
            }
        }
        log.info("rating.updated source={} jugadores tocados={}/{}", source, touched, list.size());
    }

    private boolean applyOne(Map<String, Object> p, String source, Instant at) {
        return switch (source) {
            case "LICHESS" -> byUsername(players.findByLichessUsernameIgnoreCase(Payloads.str(p, "lichessUsername")),
                    p, at, source, Map.of(RatingType.LICHESS_BULLET, "eloLichessBullet",
                            RatingType.LICHESS_BLITZ, "eloLichessBlitz", RatingType.LICHESS_RAPID, "eloLichessRapid",
                            RatingType.LICHESS_CLASSICAL, "eloLichessClassical"));
            case "CHESSCOM" -> byUsername(players.findByChesscomUsernameIgnoreCase(Payloads.str(p, "chesscomUsername")),
                    p, at, source, Map.of(RatingType.CHESSCOM_BULLET, "eloChesscomBullet",
                            RatingType.CHESSCOM_BLITZ, "eloChesscomBlitz", RatingType.CHESSCOM_RAPID, "eloChesscomRapid",
                            RatingType.CHESSCOM_DAILY, "eloChesscomDaily"));
            default -> federated(p, at, source);
        };
    }

    /** Plataformas online: solo si el jugador vinculó ese username. */
    private boolean byUsername(Optional<Player> match, Map<String, Object> p, Instant at, String source,
                               Map<RatingType, String> fields) {
        if (match.isEmpty()) return false;
        Player player = match.get();
        boolean changed = false;
        for (var e : fields.entrySet()) {
            changed |= ratings.apply(player, e.getKey(), Payloads.integer(p, e.getValue()), at, source);
        }
        if (changed) markEnriched(player, source);
        return changed;
    }

    /** Federados (FIDE, AJEFECH): identifica o crea, completa datos vacíos y actualiza ELO. */
    private boolean federated(Map<String, Object> p, Instant at, String source) {
        String firstName = Payloads.str(p, "firstName");
        String lastName = Payloads.str(p, "lastName");
        if (firstName == null || lastName == null) return false;

        String fed = Payloads.str(p, "federationId");
        String fide = Payloads.str(p, "fideId");
        String rutHash = hasher.rut(Payloads.str(p, "rut"));
        if (suppressions.anySuppressed(Arrays.asList(hasher.federationId(fed), hasher.fideId(fide), rutHash))) {
            log.debug("rating.updated: identificador suprimido, se omite");
            return false;
        }

        Player player = lookup(fed, fide, rutHash).orElseGet(() -> create(firstName, lastName));
        if (player == null) return false;

        if (player.getFederationId() == null) player.setFederationId(fed);
        if (player.getFideId() == null) player.setFideId(fide);
        if (player.getRutHash() == null) player.setRutHash(rutHash);
        if (player.getBirthYear() == null) player.setBirthYear(birthYear(p));
        if (player.getSourceUrl() == null) player.setSourceUrl(Payloads.str(p, "sourceUrl"));
        String period = Payloads.str(p, "period");
        if (period != null) player.setSourcePeriod(period);
        String clubName = Payloads.str(p, "clubName");
        if (clubName != null && player.getClub() == null) player.setClub(findOrCreateClub(clubName));

        updateTitle(player, Payloads.str(p, "title"), period, source);

        ratings.apply(player, RatingType.NATIONAL, Payloads.integer(p, "eloNational"), at, source);
        ratings.apply(player, RatingType.FIDE_STANDARD, Payloads.integer(p, "eloFideStandard"), at, source);
        ratings.apply(player, RatingType.FIDE_RAPID, Payloads.integer(p, "eloFideRapid"), at, source);
        ratings.apply(player, RatingType.FIDE_BLITZ, Payloads.integer(p, "eloFideBlitz"), at, source);
        markEnriched(player, source);
        return true;
    }

    /** Título FIDE vigente (GM, IM, ...): cierra el anterior si cambió. Valores desconocidos se ignoran. */
    private void updateTitle(Player player, String raw, String period, String source) {
        if (raw == null || player.getId() == null) return;
        PlayerTitle.Title title;
        try {
            title = PlayerTitle.Title.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        var current = titles.findFirstByPlayerIdAndCurrentTrue(player.getId());
        if (current.map(t -> t.getTitle() == title).orElse(false)) return;
        current.ifPresent(t -> { t.close(); titles.save(t); });
        LocalDate since = period == null ? LocalDate.now() : LocalDate.parse(period + "-01");
        titles.save(PlayerTitle.current(player.getId(), title, since, source));
    }

    /** {@code birthYear} explícito (FIDE solo publica el año) o el año de {@code birthDate}. */
    private static Integer birthYear(Map<String, Object> p) {
        Integer year = Payloads.integer(p, "birthYear");
        if (year != null) return year;
        var date = Payloads.date(p, "birthDate");
        return date == null ? null : date.getYear();
    }

    private Optional<Player> lookup(String fed, String fide, String rutHash) {
        Optional<Player> found = fed == null ? Optional.empty() : players.findByFederationId(fed);
        if (found.isEmpty() && fide != null) found = players.findByFideId(fide);
        if (found.isEmpty() && rutHash != null) found = players.findByRutHash(rutHash);
        return found;
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

    private void markEnriched(Player player, String source) {
        player.setEnrichmentSource(source);
        player.setEnrichedAt(Instant.now());
        players.save(player);
    }
}
