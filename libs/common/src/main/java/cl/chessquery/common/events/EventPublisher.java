package cl.chessquery.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

/**
 * Publica eventos al exchange ChessEvents. Best-effort: un fallo del broker se
 * loguea y no rompe la transacción de negocio (mismo criterio que la v2; si un
 * flujo exige at-least-once se agrega outbox en ese servicio).
 */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String routingKey, Map<String, Object> payload) {
        ChessEvent event = ChessEvent.of(routingKey, payload);
        try {
            rabbitTemplate.convertAndSend(ChessEvents.EXCHANGE, routingKey, event);
            log.debug("Evento publicado {} → {}", routingKey, event.eventId());
        } catch (RuntimeException e) {
            log.error("Error publicando evento {}: {}", routingKey, e.getMessage());
        }
    }
}
