package cl.chessquery.users.rating;

import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.common.events.Payloads;
import cl.chessquery.users.events.UsersEvents;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consume {@code rating.updated} (lo publica el ETL): {@code { source, period?, players: [ {...} ] }}.
 * Solo reparte: cada jugador del lote lo aplica la {@link RatingSource} que atiende esa {@code source}
 * (federadas: {@link FederatedRatingSource}; plataformas online: {@link LinkedAccountRatingSource}).
 * Un jugador con error no tumba el lote: se registra y se sigue con el resto.
 */
@Slf4j
@Component
public class RatingUpdatedConsumer {

    private final IdempotentConsumer idempotent;
    private final Map<String, RatingSource> sourcesByName = new HashMap<>();

    public RatingUpdatedConsumer(IdempotentConsumer idempotent, List<RatingSource> sources) {
        this.idempotent = idempotent;
        sources.forEach(s -> s.sources().forEach(name -> sourcesByName.put(name, s)));
    }

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
        RatingSource handler = source == null ? null : sourcesByName.get(source.toUpperCase());
        if (handler == null || !(event.payload().get("players") instanceof List<?> list)) {
            log.info("rating.updated sin fuente conocida ({}) o sin jugadores; nada que hacer", source);
            return;
        }
        long touched = list.stream().filter(item -> applySafely(handler, item, source.toUpperCase(), event.timestamp())).count();
        log.info("rating.updated source={} jugadores tocados={}/{}", source, touched, list.size());
    }

    @SuppressWarnings("unchecked")
    private boolean applySafely(RatingSource handler, Object item, String source, Instant at) {
        if (!(item instanceof Map<?, ?> raw)) return false;
        Map<String, Object> player = (Map<String, Object>) raw;
        try {
            return handler.apply(player, source, at);
        } catch (RuntimeException e) {
            log.warn("rating.updated: jugador {} ignorado: {}", player.get("federationId"), e.getMessage());
            return false;
        }
    }
}
