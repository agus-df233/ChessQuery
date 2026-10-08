package cl.chessquery.users;

import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.common.events.ProcessedEvent;
import cl.chessquery.common.events.ProcessedEventRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Servicio <b>users</b>: jugadores e identidad interna, catálogo (países/clubes federativos),
 * ratings por modalidad e historial, ranking nacional, organización del organizador (club como
 * tenant) con su roster provisorio, y amistades.
 *
 * <p>La tabla {@code processed_event} y su repositorio viven en la librería común, por eso el
 * escaneo de entidades y repositorios incluye ese paquete además del propio.
 */
@SpringBootApplication
@EntityScan(basePackageClasses = {UsersApplication.class, ProcessedEvent.class})
@EnableJpaRepositories(basePackageClasses = {UsersApplication.class, ProcessedEventRepository.class})
public class UsersApplication {

    /**
     * Tareas programadas (pedido diario de ratings externos). Se apagan en las pruebas con
     * {@code chessquery.scheduling.enabled=false}: allí se llaman a mano.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "chessquery.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {}

    public static void main(String[] args) {
        SpringApplication.run(UsersApplication.class, args);
    }

    /** Consumidor idempotente compartido por todos los @SqsListener del servicio. */
    @Bean
    public IdempotentConsumer idempotentConsumer(ProcessedEventRepository repository) {
        return new IdempotentConsumer(repository);
    }
}
