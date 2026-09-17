package cl.chessquery.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope de todo evento del exchange {@code ChessEvents}.
 * Catálogo y fuente de verdad: docs/events.md.
 */
public record ChessEvent(UUID eventId, String eventType, Instant timestamp, Map<String, Object> payload) {

    public static ChessEvent of(String eventType, Map<String, Object> payload) {
        return new ChessEvent(UUID.randomUUID(), eventType, Instant.now(), payload);
    }
}
