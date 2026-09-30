package cl.chessquery.game;

import cl.chessquery.game.domain.Game;
import cl.chessquery.game.domain.GameRepository;
import cl.chessquery.game.domain.GameStatus;
import cl.chessquery.game.live.LiveConnections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Barrido de cada segundo: cierra por tiempo las partidas cuyo reloj llegó a cero aunque nadie esté conectado, y
 * vence los desafíos sin respuesta. Si otra instancia (o una jugada) cambió la partida al mismo tiempo, el bloqueo
 * optimista ({@code version}) descarta este intento y se reintenta en el siguiente barrido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameSweeper {

    private final GameRepository games;
    private final GameService service;
    private final GameNotifier notifier;
    private final TransactionTemplate tx;
    private final LiveConnections live;
    private final Clock clock;

    @Value("${chessquery.games.challenge-ttl:10m}")
    private Duration challengeTtl;

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void sweep() {
        Instant now = clock.instant();
        games.findByStatus(GameStatus.ACTIVE).stream()
                .filter(g -> g.remainingMs(g.whiteToMove(), now) <= 0)
                .forEach(g -> safely(g.getId(), () -> flagTimeout(g.getId(), now)));
        games.findByStatusAndCreatedAtBefore(GameStatus.PENDING, now.minus(challengeTtl))
                .forEach(g -> safely(g.getId(), () -> expire(g.getId())));
    }

    /** Conexiones en vivo sin actividad por más de 2 h (el máximo que API Gateway mantiene una conexión). */
    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void purgeLiveConnections() {
        int purged = live.purgeSeenBefore(clock.instant().minus(Duration.ofHours(2)));
        if (purged > 0) log.info("Conexiones en vivo vencidas borradas: {}", purged);
    }

    private void flagTimeout(long id, Instant now) {
        Game g = games.findById(id).orElseThrow();
        if (g.getStatus() != GameStatus.ACTIVE || g.remainingMs(g.whiteToMove(), now) > 0) return;
        service.timeout(g, g.whiteToMove(), now);
        games.saveAndFlush(g);
        notifier.changedAfterCommit(id);
    }

    private void expire(long id) {
        Game g = games.findById(id).orElseThrow();
        if (g.getStatus() != GameStatus.PENDING) return;
        g.setStatus(GameStatus.EXPIRED);
        games.saveAndFlush(g);
        notifier.changedAfterCommit(id);
    }

    private void safely(long id, Runnable action) {
        try {
            tx.executeWithoutResult(s -> action.run());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Partida {} cambió durante el barrido; se reintenta", id);
        }
    }
}
