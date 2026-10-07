package cl.chessquery.game.room;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Avisa que una sala cambió, tras el commit, por las mismas dos vías que {@code GameNotifier}: el WebSocket
 * ({@link RoomChanged} → {@code LiveConnections}) y los long polls de respaldo en espera de esta instancia.
 * Los long polls se despiertan en otro hilo, con lecturas frescas: la sesión de Hibernate del request que guardó puede
 * tener en caché partidas de la sala cargadas antes de otro cambio simultáneo (ver {@code TournamentLive}).
 */
@Component
@RequiredArgsConstructor
public class RoomNotifier {

    private final Map<Long, List<Runnable>> waiters = new ConcurrentHashMap<>();
    private final Executor wake = Executors.newVirtualThreadPerTaskExecutor();
    private final ApplicationEventPublisher events;

    public void await(long roomId, Runnable onChange) {
        waiters.computeIfAbsent(roomId, id -> new CopyOnWriteArrayList<>()).add(onChange);
    }

    public void forget(long roomId, Runnable onChange) {
        List<Runnable> list = waiters.get(roomId);
        if (list != null) list.remove(onChange);
    }

    public void changedAfterCommit(long roomId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fire(roomId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fire(roomId);
            }
        });
    }

    void fire(long roomId) {
        List<Runnable> list = waiters.remove(roomId);
        if (list != null) list.forEach(wake::execute);
        events.publishEvent(new RoomChanged(roomId));
    }
}
