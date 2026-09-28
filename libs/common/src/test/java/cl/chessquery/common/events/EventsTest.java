package cl.chessquery.common.events;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventsTest {

    @Test
    void publisherSendsEnvelopeWithEventTypeAttributeAndSwallowsBusErrors() throws Exception {
        SnsClient sns = mock(SnsClient.class);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        EventPublisher publisher = new EventPublisher(sns, json, "arn:aws:sns:us-east-1:000000000000:chess-events");

        publisher.publish("player.claimed", Map.of("playerId", 1));
        ArgumentCaptor<PublishRequest> req = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(req.capture());
        assertThat(req.getValue().topicArn()).endsWith(":chess-events");
        assertThat(req.getValue().messageAttributes().get(ChessEvents.EVENT_TYPE_ATTRIBUTE).stringValue())
                .isEqualTo("player.claimed");
        ChessEvent sent = json.readValue(req.getValue().message(), ChessEvent.class);
        assertThat(sent.eventType()).isEqualTo("player.claimed");
        assertThat(sent.payload()).containsEntry("playerId", 1);

        when(sns.publish(any(PublishRequest.class))).thenThrow(SnsException.builder().message("caído").build());
        publisher.publish("x", Map.of()); // no lanza
    }

    @Test
    void chessEventOfFillsIdAndTimestamp() {
        ChessEvent e = ChessEvent.of("game.finished", Map.of("gameId", 9));
        assertThat(e.eventId()).isNotNull();
        assertThat(e.timestamp()).isNotNull();
        assertThat(e.eventType()).isEqualTo("game.finished");
    }

    @Test
    void idempotentConsumerProcessesOnce() {
        ProcessedEventRepository repo = mock(ProcessedEventRepository.class);
        IdempotentConsumer consumer = new IdempotentConsumer(repo);
        ChessEvent e = ChessEvent.of("elo.updated", Map.of());
        AtomicInteger calls = new AtomicInteger();

        when(repo.existsById(e.eventId())).thenReturn(false, true);
        assertThat(consumer.handle(e, ev -> calls.incrementAndGet())).isTrue();
        assertThat(consumer.handle(e, ev -> calls.incrementAndGet())).isFalse();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void idempotentConsumerHandlesConcurrentInsertRace() {
        ProcessedEventRepository repo = mock(ProcessedEventRepository.class);
        IdempotentConsumer consumer = new IdempotentConsumer(repo);
        ChessEvent e = new ChessEvent(UUID.randomUUID(), "x", java.time.Instant.now(), Map.of());
        when(repo.existsById(e.eventId())).thenReturn(false);
        when(repo.saveAndFlush(any(ProcessedEvent.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThat(consumer.handle(e, ev -> { throw new AssertionError("no debe ejecutarse"); })).isFalse();
    }

    @Test
    void processedEventExposesFields() {
        ProcessedEvent p = new ProcessedEvent(UUID.randomUUID(), "t");
        assertThat(p.getEventType()).isEqualTo("t");
        assertThat(p.getProcessedAt()).isNotNull();
        assertThat(p.getEventId()).isNotNull();
    }

    @Test
    void noopBroadcasterDoesNothing() {
        cl.chessquery.common.realtime.EventBroadcaster.NOOP.broadcast("game.1", "move.played", Map.of());
    }
}
