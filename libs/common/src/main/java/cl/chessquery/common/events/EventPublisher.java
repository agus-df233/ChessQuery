package cl.chessquery.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

/**
 * Publica eventos en el tópico SNS {@code chess-events}: el cuerpo es el envelope {@link ChessEvent}
 * en JSON y el atributo {@code eventType} lleva el routing key para las filter policies de cada
 * cola. Best-effort: un fallo del bus se loguea y no rompe la transacción de negocio (mismo
 * criterio que la v2; si un flujo exige at-least-once se agrega outbox en ese servicio).
 */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final SnsClient sns;
    private final ObjectMapper json;
    private final String topicArn;

    public EventPublisher(SnsClient sns, ObjectMapper json, String topicArn) {
        this.sns = sns;
        this.json = json;
        this.topicArn = topicArn;
    }

    public void publish(String routingKey, Map<String, Object> payload) {
        ChessEvent event = ChessEvent.of(routingKey, payload);
        try {
            sns.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(json.writeValueAsString(event))
                    .messageAttributes(Map.of(ChessEvents.EVENT_TYPE_ATTRIBUTE, MessageAttributeValue.builder()
                            .dataType("String").stringValue(routingKey).build()))
                    .build());
            log.debug("Evento publicado {} → {}", routingKey, event.eventId());
        } catch (Exception e) {
            log.error("Error publicando evento {}: {}", routingKey, e.getMessage());
        }
    }
}
