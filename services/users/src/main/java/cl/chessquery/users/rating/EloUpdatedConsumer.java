package cl.chessquery.users.rating;

import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.common.events.Payloads;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.PlayerRepository;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume {@code elo.updated} (lo emiten game al cerrar una partida y tournament al cerrar un torneo, uno por
 * jugador): {@code { playerId, oldElo, newElo, delta, ratingType, source?, gameId?, tournamentId? }}. Actualiza el
 * snapshot y el historial con la fuente indicada (GAME por defecto). No republica nada (evita bucles).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EloUpdatedConsumer {

    private final IdempotentConsumer idempotent;
    private final PlayerRepository players;
    private final RatingService ratings;

    /** Cola SQS dedicada, suscrita al tópico con filter policy por eventType (docs/events.md). */
    @SqsListener("${chessquery.events.queues.elo}")
    public void onEloUpdated(ChessEvent event) {
        if (!UsersEvents.ELO_UPDATED.equals(event.eventType())) return;
        idempotent.handle(event, this::apply);
    }

    /** Visible para pruebas; la idempotencia la aplica el listener. */
    public void apply(ChessEvent event) {
        Map<String, Object> p = event.payload();
        Long playerId = Payloads.lng(p, "playerId");
        Integer newElo = Payloads.integer(p, "newElo");
        String typeName = Payloads.str(p, "ratingType");
        if (playerId == null || newElo == null || typeName == null) {
            log.warn("elo.updated incompleto: {}", p);
            return;
        }
        RatingType type;
        try {
            type = RatingType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            log.warn("elo.updated con ratingType desconocido: {}", typeName);
            return;
        }
        players.findById(playerId).ifPresentOrElse(
                player -> ratings.apply(player, type, newElo, event.timestamp(), source(p)),
                () -> log.warn("elo.updated para jugador inexistente {}", playerId));
    }

    /** Fuente del historial: TOURNAMENT si lo dice el evento; cualquier otra cosa se registra como GAME. */
    private static String source(Map<String, Object> p) {
        return "TOURNAMENT".equals(Payloads.str(p, "source")) ? "TOURNAMENT" : "GAME";
    }
}
