package cl.chessquery.game;

import cl.chessquery.game.live.GameChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Avisa que una partida cambió, tras el commit (para que todos lean el estado nuevo), por dos vías:
 * <ul>
 *   <li><b>WebSocket</b>: publica {@link GameChanged}; {@code LiveConnections} lo envía a todas las conexiones de la
 *       partida (viven en la base de datos, así que sirve con varias instancias).</li>
 *   <li><b>Long polling</b> (respaldo): despierta los {@code GET ?afterVersion=n} en espera de esta instancia; con varias
 *       instancias, un cliente atendido por otra se entera al vencer su espera (25 s).</li>
 * </ul>
 * La API REST sigue siendo la fuente de verdad (ADR-0002, enmiendas del 29 y 30-09-2026).
 */
@Component
@RequiredArgsConstructor
public class GameNotifier {

    private final Map<Long, List<Runnable>> waiters = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    public void await(long gameId, Runnable onChange) {
        waiters.computeIfAbsent(gameId, id -> new CopyOnWriteArrayList<>()).add(onChange);
    }

    public void forget(long gameId, Runnable onChange) {
        List<Runnable> list = waiters.get(gameId);
        if (list != null) list.remove(onChange);
    }

    /** Llamar dentro de la transacción que modifica la partida. */
    public void changedAfterCommit(long gameId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fire(gameId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fire(gameId);
            }
        });
    }

    void fire(long gameId) {
        List<Runnable> list = waiters.remove(gameId);
        if (list != null) list.forEach(Runnable::run);
        events.publishEvent(new GameChanged(gameId));
    }
}
