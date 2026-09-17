package cl.chessquery.users.rating;

import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.users.catalog.Club;
import cl.chessquery.users.catalog.ClubRepository;
import cl.chessquery.users.events.Payloads;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consume {@code rating.updated} (lo emite el ETL tras sincronizar una fuente):
 * {@code { source: AJEFECH|LICHESS|CHESSCOM, players: [ {...} ] }}.
 *
 * <ul>
 *   <li><b>AJEFECH</b>: identifica al jugador por federationId → fideId → rut → nombre completo;
 *       si no existe lo crea como fila federada (sin cuenta). Completa datos vacíos, nunca
 *       pisa los curados a mano; los ELO sí se actualizan.</li>
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

    @RabbitListener(queues = UsersEvents.RATING_QUEUE)
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

    /** Federación (AJEFECH): identifica o crea, completa datos vacíos y actualiza ELO. */
    private boolean federated(Map<String, Object> p, Instant at, String source) {
        String firstName = Payloads.str(p, "firstName");
        String lastName = Payloads.str(p, "lastName");
        if (firstName == null || lastName == null) return false;

        Player player = lookup(p, firstName, lastName).orElseGet(() -> create(p, firstName, lastName));
        if (player == null) return false;

        if (player.getFederationId() == null) player.setFederationId(Payloads.str(p, "federationId"));
        if (player.getFideId() == null) player.setFideId(Payloads.str(p, "fideId"));
        if (player.getRut() == null) player.setRut(Payloads.str(p, "rut"));
        if (player.getBirthDate() == null) player.setBirthDate(Payloads.date(p, "birthDate"));
        String clubName = Payloads.str(p, "clubName");
        if (clubName != null && player.getClub() == null) player.setClub(findOrCreateClub(clubName));

        ratings.apply(player, RatingType.NATIONAL, Payloads.integer(p, "eloNational"), at, source);
        ratings.apply(player, RatingType.FIDE_STANDARD, Payloads.integer(p, "eloFideStandard"), at, source);
        markEnriched(player, source);
        return true;
    }

    private Optional<Player> lookup(Map<String, Object> p, String firstName, String lastName) {
        String fed = Payloads.str(p, "federationId");
        String fide = Payloads.str(p, "fideId");
        String rut = Payloads.str(p, "rut");
        Optional<Player> found = fed == null ? Optional.empty() : players.findByFederationId(fed);
        if (found.isEmpty() && fide != null) found = players.findByFideId(fide);
        if (found.isEmpty() && rut != null) found = players.findByRut(rut);
        if (found.isEmpty()) found = players.findByFullNameIgnoreCase(firstName + " " + lastName);
        return found;
    }

    private Player create(Map<String, Object> p, String firstName, String lastName) {
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
