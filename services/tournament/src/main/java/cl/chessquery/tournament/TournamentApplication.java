package cl.chessquery.tournament;

import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.common.events.ProcessedEvent;
import cl.chessquery.common.events.ProcessedEventRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Servicio <b>tournament</b>: torneos presenciales que dirige el organizador de un club (inscripción, pareo suizo o
 * round robin, resultados por mesa, tabla con desempates, cierre con rating de plataforma y exportación TRF), su
 * vista pública, y el calendario de torneos de la Federación que trae el ETL.
 */
@SpringBootApplication
@EntityScan(basePackageClasses = {TournamentApplication.class, ProcessedEvent.class})
@EnableJpaRepositories(basePackageClasses = {TournamentApplication.class, ProcessedEventRepository.class},
        considerNestedRepositories = true)
public class TournamentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TournamentApplication.class, args);
    }

    @Bean
    public IdempotentConsumer idempotentConsumer(ProcessedEventRepository repository) {
        return new IdempotentConsumer(repository);
    }
}
