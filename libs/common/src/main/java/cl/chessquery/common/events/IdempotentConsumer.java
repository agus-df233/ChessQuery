package cl.chessquery.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * Envuelve el manejo de un {@link ChessEvent} garantizando que se procese una sola vez.
 * Uso en un @SqsListener: {@code idempotent.handle(event, this::apply)}. SQS entrega al menos una
 * vez, así que los duplicados son esperables y se descartan aquí.
 */
public class IdempotentConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

    private final ProcessedEventRepository repository;

    public IdempotentConsumer(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /** @return true si el evento se procesó, false si era un duplicado. */
    @Transactional
    public boolean handle(ChessEvent event, Consumer<ChessEvent> handler) {
        if (repository.existsById(event.eventId())) {
            log.debug("Evento duplicado ignorado {} ({})", event.eventId(), event.eventType());
            return false;
        }
        try {
            repository.saveAndFlush(new ProcessedEvent(event.eventId(), event.eventType()));
        } catch (DataIntegrityViolationException race) {
            log.debug("Evento {} procesado concurrentemente por otra instancia", event.eventId());
            return false;
        }
        handler.accept(event);
        return true;
    }
}
