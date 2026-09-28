package cl.chessquery.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.autoconfigure.sns.SnsAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Registra el {@link EventPublisher} sobre el {@link SnsClient} de Spring Cloud AWS cuando el
 * servicio declara {@code chessquery.events.topic-arn}. Las colas SQS, sus suscripciones
 * (filter policy por {@code eventType}) y las DLQ son infraestructura: viven en Terraform
 * (nube) y en {@code infra/localstack} (local), no en el código.
 */
@AutoConfiguration(after = {SnsAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnClass(SnsClient.class)
@ConditionalOnBean(SnsClient.class)
@ConditionalOnProperty("chessquery.events.topic-arn")
public class ChessEventsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(SnsClient sns, ObjectProvider<ObjectMapper> mapper,
                                         @Value("${chessquery.events.topic-arn}") String topicArn) {
        return new EventPublisher(sns, mapper.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()),
                topicArn);
    }
}
