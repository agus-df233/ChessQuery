package cl.chessquery.tournament;

import cl.chessquery.tournament.domain.Repositories;
import lombok.RequiredArgsConstructor;
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
 * Tiempo real del torneo, igual que las partidas ({@code GameNotifier} en game): cada cambio sube la versión del torneo
 * en la misma transacción y, tras el commit, despierta a los long polls en espera (la pantalla de la sala, los
 * apoderados siguiendo a un jugador). Con varias instancias, quien espera en otra se entera al vencer su espera (25 s).
 * <p>
 * Los que esperan se despiertan en <b>otro hilo</b>: si armaran la respuesta en el hilo del request que acaba de guardar,
 * leerían con su sesión de Hibernate, que guarda en caché las mesas que cargó antes; con dos resultados casi simultáneos
 * la pantalla recibía la versión nueva con una mesa vieja y ya no volvía a preguntar.
 */
@Component
@RequiredArgsConstructor
public class TournamentLive {

    private final Map<Long, List<Runnable>> waiters = new ConcurrentHashMap<>();
    private final Executor wake = Executors.newVirtualThreadPerTaskExecutor();
    private final Repositories.Tournaments tournaments;

    /** Llamar dentro de la transacción que cambia el torneo. */
    public void changed(long tournamentId) {
        tournaments.bumpVersion(tournamentId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fire(tournamentId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fire(tournamentId);
            }
        });
    }

    public void await(long tournamentId, Runnable onChange) {
        waiters.computeIfAbsent(tournamentId, id -> new CopyOnWriteArrayList<>()).add(onChange);
    }

    public void forget(long tournamentId, Runnable onChange) {
        List<Runnable> list = waiters.get(tournamentId);
        if (list != null) list.remove(onChange);
    }

    void fire(long tournamentId) {
        List<Runnable> list = waiters.remove(tournamentId);
        if (list != null) list.forEach(wake::execute);
    }
}
