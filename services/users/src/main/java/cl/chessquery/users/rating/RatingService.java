package cl.chessquery.users.rating;

import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;

/**
 * Único punto que escribe ratings: actualiza el snapshot en {@code player} y agrega el punto
 * a {@code rating_history}. Lo usan el consumer de {@code elo.updated} (partidas), el de
 * {@code rating.updated} (ETL) y la sincronización manual con Lichess/Chess.com.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    /** Ventana máxima del gráfico de progreso. */
    public static final int MAX_MONTHS = 60;

    private final PlayerRepository players;
    private final RatingHistoryRepository history;

    /**
     * Aplica un nuevo valor si cambia. Devuelve true si hubo cambio.
     * @param recordedAt momento del evento (no "ahora"), para que la serie respete el orden real
     */
    @Transactional
    public boolean apply(Player player, RatingType type, Integer newValue, Instant recordedAt, String source) {
        if (newValue == null || newValue <= 0) return false;
        Integer previous = player.rating(type);
        if (newValue.equals(previous)) return false;
        player.setRating(type, newValue);
        players.save(player);
        history.save(new RatingHistory(player.getId(), type, newValue, previous,
                recordedAt != null ? recordedAt : Instant.now(), source));
        log.debug("Rating {} de jugador {}: {} → {} ({})", type, player.getId(), previous, newValue, source);
        return true;
    }

    /** Serie de los últimos {@code months} meses (acotado a {@link #MAX_MONTHS}). */
    @Transactional(readOnly = true)
    public List<RatingHistory.Point> series(Long playerId, RatingType type, int months) {
        int window = Math.max(1, Math.min(months, MAX_MONTHS));
        Instant since = LocalDate.now(ZoneOffset.UTC).minusMonths(window).atStartOfDay().toInstant(ZoneOffset.UTC);
        return history.findByPlayerIdAndRatingTypeAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(playerId, type, since)
                .stream().map(RatingHistory.Point::of).toList();
    }
}
