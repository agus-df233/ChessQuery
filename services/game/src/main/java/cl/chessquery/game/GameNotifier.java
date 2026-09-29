package cl.chessquery.game;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Avisa a los long polls en espera cuando una partida cambia (tras el commit, para que lean el estado nuevo).
 * Vive en memoria de la instancia: con varias instancias, un cliente atendido por otra se entera al vencer su espera
 * (25 s) o en su siguiente consulta; la API REST sigue siendo la fuente de verdad (ADR-0002, enmienda).
 */
@Component
public class GameNotifier {

    private final Map<Long, List<Runnable>> waiters = new ConcurrentHashMap<>();

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
    }
}
