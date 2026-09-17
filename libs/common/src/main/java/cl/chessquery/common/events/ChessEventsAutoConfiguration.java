package cl.chessquery.common.events;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Declara el exchange {@code ChessEvents} (topic, durable), la serialización JSON
 * y el {@link EventPublisher}. Las colas y bindings son responsabilidad de cada
 * servicio consumidor (una cola dedicada por servicio; nunca compartida).
 */
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(ConnectionFactory.class)
public class ChessEventsAutoConfiguration {

    @Bean
    public TopicExchange chessEventsExchange() {
        return ExchangeBuilder.topicExchange(ChessEvents.EXCHANGE).durable(true).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory cf,
                                                                              MessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(converter);
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(RabbitTemplate rabbitTemplate) {
        return new EventPublisher(rabbitTemplate);
    }
}
