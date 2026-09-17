package cl.chessquery.common;

import cl.chessquery.common.api.ApiErrorAutoConfiguration;
import cl.chessquery.common.api.GlobalExceptionHandler;
import cl.chessquery.common.events.ChessEvents;
import cl.chessquery.common.events.ChessEventsAutoConfiguration;
import cl.chessquery.common.events.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AutoConfigurationsTest {

    @Configuration
    static class RabbitStub {
        @Bean ConnectionFactory connectionFactory() { return mock(ConnectionFactory.class); }
    }

    @Test
    void eventsAutoConfigDeclaresExchangePublisherAndListenerFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(RabbitStub.class)
                .withConfiguration(AutoConfigurations.of(ChessEventsAutoConfiguration.class))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(EventPublisher.class);
                    assertThat(ctx).hasSingleBean(RabbitTemplate.class);
                    assertThat(ctx).hasSingleBean(SimpleRabbitListenerContainerFactory.class);
                    assertThat(ctx.getBean(TopicExchange.class).getName()).isEqualTo(ChessEvents.EXCHANGE);
                });
    }

    @Test
    void eventsAutoConfigBacksOffWithoutConnectionFactory() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ChessEventsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(EventPublisher.class));
    }

    @Test
    void apiErrorAutoConfigRegistersHandlerInServletApps() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApiErrorAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class));
    }
}
