package cl.chessquery.game;

import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.common.rating.EloCalculator;
import cl.chessquery.game.domain.Game;
import cl.chessquery.game.domain.GameStatus;
import cl.chessquery.game.domain.Outcome;
import cl.chessquery.game.domain.Termination;
import cl.chessquery.game.events.GameEvents;
import cl.chessquery.game.rules.ChessRules;
import io.github.wolfraam.chessgame.pgn.PGNTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Cierra una partida: resultado, PGN y, si es por rating, el nuevo rating de plataforma de cada jugador
 * ({@code elo.updated}, fuente GAME) + {@code game.finished}. users actualiza rating e historial.
 * No guarda: quien la llama persiste la partida y avisa a los long polls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameFinisher {

    private static final DateTimeFormatter PGN_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.of("America/Santiago"));

    private final EventPublisher events;

    public void finish(Game g, Outcome outcome, Termination termination, Instant now) {
        g.setStatus(GameStatus.FINISHED);
        g.setResult(outcome);
        g.setTermination(termination);
        g.setDrawOfferBy(null);
        g.setFinishedAt(now);
        g.setPgn(ChessRules.pgn(g.uciMoves(), pgnTags(g, now)));
        if (g.isRated() && g.ply() >= 2) rate(g, outcome);
        Map<String, Object> payload = new HashMap<>();
        payload.put("gameId", g.getId());
        payload.put("whitePlayerId", g.getWhitePlayerId());
        payload.put("blackPlayerId", g.getBlackPlayerId());
        payload.put("result", outcome.name());
        payload.put("termination", termination.name());
        payload.put("rated", g.isRated());
        events.publish(GameEvents.GAME_FINISHED, payload);
        log.info("Partida {} terminada: {} por {}", g.getId(), outcome, termination);
    }

    /** Sin rating si no se alcanzó a jugar (menos de una jugada por lado): se registra el resultado igual. */
    private void rate(Game g, Outcome outcome) {
        int white = g.getWhiteRatingBefore();
        int black = g.getBlackRatingBefore();
        g.setWhiteRatingAfter(EloCalculator.next(white, g.isWhiteUnrated(), black, outcome.scoreFor(true)));
        g.setBlackRatingAfter(EloCalculator.next(black, g.isBlackUnrated(), white, outcome.scoreFor(false)));
        publishElo(g, g.getWhitePlayerId(), white, g.getWhiteRatingAfter());
        publishElo(g, g.getBlackPlayerId(), black, g.getBlackRatingAfter());
    }

    private void publishElo(Game g, long playerId, int before, int after) {
        events.publish(GameEvents.ELO_UPDATED, Map.of("playerId", playerId, "oldElo", before, "newElo", after,
                "delta", after - before, "ratingType", g.category().ratingType(), "source", "GAME", "gameId", g.getId()));
    }

    private static Map<PGNTag, String> pgnTags(Game g, Instant now) {
        Map<PGNTag, String> tags = new EnumMap<>(PGNTag.class);
        tags.put(PGNTag.EVENT, g.isRated() ? "Partida por rating ChessQuery" : "Partida amistosa ChessQuery");
        tags.put(PGNTag.SITE, "ChessQuery");
        tags.put(PGNTag.DATE, PGN_DATE.format(g.getStartedAt() == null ? now : g.getStartedAt()));
        tags.put(PGNTag.WHITE, g.getWhiteName());
        tags.put(PGNTag.BLACK, g.getBlackName());
        tags.put(PGNTag.RESULT, g.getResult().pgn());
        tags.put(PGNTag.WHITE_ELO, String.valueOf(g.getWhiteRatingBefore()));
        tags.put(PGNTag.BLACK_ELO, String.valueOf(g.getBlackRatingBefore()));
        tags.put(PGNTag.TIME_CONTROL, g.getInitialSeconds() + "+" + g.getIncrementSeconds());
        tags.put(PGNTag.TERMINATION, g.getTermination().label());
        return tags;
    }
}
