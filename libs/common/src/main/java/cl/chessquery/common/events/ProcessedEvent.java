package cl.chessquery.common.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla de idempotencia por servicio: cada consumer registra el eventId
 * procesado y descarta repetidos. Cada servicio la crea en su propio schema
 * con Flyway (ver plantilla en docs/events.md).
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(UUID eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }

    public String getEventType() { return eventType; }

    public Instant getProcessedAt() { return processedAt; }
}
