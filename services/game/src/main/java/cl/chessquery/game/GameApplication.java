package cl.chessquery.game;

import cl.chessquery.common.events.ProcessedEvent;
import cl.chessquery.common.events.ProcessedEventRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio <b>game</b>: partidas en línea entre jugadores. Desafío y aceptación, jugadas validadas en el servidor,
 * reloj del servidor (con barrido que cierra por tiempo aunque el cliente se desconecte), fin por mate, tablas o
 * abandono, PGN y rating de plataforma. Tiempo real por long polling (funciona detrás de API Gateway y del ALB).
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackageClasses = {GameApplication.class, ProcessedEvent.class})
@EnableJpaRepositories(basePackageClasses = {GameApplication.class, ProcessedEventRepository.class})
public class GameApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameApplication.class, args);
    }
}
