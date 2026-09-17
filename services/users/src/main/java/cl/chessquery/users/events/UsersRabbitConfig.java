package cl.chessquery.users.events;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas dedicadas de este servicio sobre el exchange {@code ChessEvents} (declarado en la
 * librería común). Una cola por flujo: nunca se comparte una cola entre servicios.
 */
@Configuration
public class UsersRabbitConfig {

    @Bean
    public Queue usersEloQueue() {
        return QueueBuilder.durable(UsersEvents.ELO_QUEUE).build();
    }

    @Bean
    public Binding usersEloBinding(Queue usersEloQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(usersEloQueue).to(chessEventsExchange).with(UsersEvents.ELO_UPDATED);
    }

    @Bean
    public Queue usersRatingQueue() {
        return QueueBuilder.durable(UsersEvents.RATING_QUEUE).build();
    }

    @Bean
    public Binding usersRatingBinding(Queue usersRatingQueue, TopicExchange chessEventsExchange) {
        return BindingBuilder.bind(usersRatingQueue).to(chessEventsExchange).with(UsersEvents.RATING_UPDATED);
    }
}
