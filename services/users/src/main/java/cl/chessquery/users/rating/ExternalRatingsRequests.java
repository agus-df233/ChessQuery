package cl.chessquery.users.rating;

import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pide al ETL (Lambda {@code external-ratings}) los ratings públicos de Lichess y Chess.com de las cuentas vinculadas,
 * con el evento {@code external.ratings.sync.requested}: al vincular una cuenta, cuando el jugador aprieta
 * «Sincronizar» y una vez al día para todos. users no llama a esas APIs: la respuesta vuelve como
 * {@code rating.updated} y la aplica {@link LinkedAccountRatingSource}. El evento lleva solo usernames.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalRatingsRequests {

    static final int ACCOUNTS_PER_EVENT = 100;
    private static final int PAGE = 500;

    private final PlayerRepository players;
    private final EventPublisher events;

    /** Las cuentas vinculadas de un jugador (nada si no vinculó ninguna). */
    public void requestFor(Player p) {
        Map<String, Object> account = account(p);
        if (!account.isEmpty()) publish(List.of(account));
    }

    /** Todos los días (05:30 de Chile): todas las cuentas vinculadas de jugadores activos, en lotes de 100. */
    @Scheduled(cron = "${chessquery.external-ratings.cron:0 30 5 * * *}", zone = "America/Santiago")
    @Transactional(readOnly = true)
    public int requestAll() {
        int requested = 0;
        Slice<Player> page = players.findWithLinkedAccounts(PageRequest.of(0, PAGE));
        while (true) {
            List<Map<String, Object>> accounts = page.getContent().stream().map(ExternalRatingsRequests::account)
                    .filter(a -> !a.isEmpty()).toList();
            for (int i = 0; i < accounts.size(); i += ACCOUNTS_PER_EVENT) {
                publish(accounts.subList(i, Math.min(accounts.size(), i + ACCOUNTS_PER_EVENT)));
            }
            requested += accounts.size();
            if (!page.hasNext()) break;
            page = players.findWithLinkedAccounts(page.nextPageable());
        }
        log.info("Ratings externos pedidos para {} cuentas", requested);
        return requested;
    }

    private void publish(List<Map<String, Object>> accounts) {
        events.publish(UsersEvents.EXTERNAL_RATINGS_SYNC_REQUESTED, Map.of("accounts", new ArrayList<>(accounts)));
    }

    private static Map<String, Object> account(Player p) {
        Map<String, Object> a = new HashMap<>();
        if (p.getLichessUsername() != null) a.put("lichessUsername", p.getLichessUsername());
        if (p.getChesscomUsername() != null) a.put("chesscomUsername", p.getChesscomUsername());
        return a;
    }
}
