package cl.chessquery.game;

import cl.chessquery.common.events.ProcessedEvent;
import cl.chessquery.common.events.ProcessedEventRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio <b>game</b>: partidas en línea entre jugadores. Desafío y aceptación, jugadas validadas en el servidor,
 * reloj del servidor (con barrido que cierra por tiempo aunque el cliente se desconecte), fin por mate, tablas o
 * abandono, PGN y rating de plataforma. Tiempo real por long polling (funciona detrás de API Gateway y del ALB).
 */
@SpringBootApplication
@EntityScan(basePackageClasses = {GameApplication.class, ProcessedEvent.class})
@EnableJpaRepositories(basePackageClasses = {GameApplication.class, ProcessedEventRepository.class})
public class GameApplication {

    /**
     * Barridos programados (reloj, desafíos vencidos, conexiones en vivo). Se apagan en las pruebas con
     * {@code chessquery.scheduling.enabled=false}: allí los barridos se llaman a mano y no deben correr con la base
     * de datos de otra prueba ya detenida.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "chessquery.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {}

    public static void main(String[] args) {
        SpringApplication.run(GameApplication.class, args);
    }
}
