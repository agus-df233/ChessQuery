package cl.chessquery.common;

import cl.chessquery.common.api.ApiErrorAutoConfiguration;
import cl.chessquery.common.api.GlobalExceptionHandler;
import cl.chessquery.common.events.ChessEventsAutoConfiguration;
import cl.chessquery.common.events.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AutoConfigurationsTest {

    @Configuration
    static class SnsStub {
        @Bean SnsClient snsClient() { return mock(SnsClient.class); }
    }

    @Test
    void eventsAutoConfigRegistersPublisherWhenTopicIsConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(SnsStub.class)
                .withPropertyValues("chessquery.events.topic-arn=arn:aws:sns:us-east-1:000000000000:chess-events")
                .withConfiguration(AutoConfigurations.of(ChessEventsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).hasSingleBean(EventPublisher.class));
    }

    @Test
    void eventsAutoConfigBacksOffWithoutSnsClientOrTopic() {
        new ApplicationContextRunner()
                .withPropertyValues("chessquery.events.topic-arn=arn:x")
                .withConfiguration(AutoConfigurations.of(ChessEventsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(EventPublisher.class));
        new ApplicationContextRunner()
                .withUserConfiguration(SnsStub.class)
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
