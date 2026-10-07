package cl.chessquery.game.api;

import cl.chessquery.common.rating.TimeControlCategory;
import cl.chessquery.game.domain.Game;
import cl.chessquery.game.domain.GameStatus;
import cl.chessquery.game.domain.Outcome;
import cl.chessquery.game.domain.Termination;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** Contratos JSON de la API de partidas (espejo en apps/web/src/api/gameTypes.ts). */
public final class GameDtos {

    private GameDtos() {}

    public enum ColorChoice { WHITE, BLACK, RANDOM }

    public record ChallengeRequest(@NotNull Long opponentId, @Min(1) @Max(180) int minutes,
                                   @Min(0) @Max(180) int incrementSeconds, ColorChoice color, Boolean rated) {}

    public record MoveRequest(@NotBlank String uci) {}

    public record Side(Long playerId, String name, Integer ratingBefore, Integer ratingAfter, long clockMs) {}

    public record GameView(Long id, GameStatus status, Side white, Side black, Long challengerId, int initialSeconds,
                           int incrementSeconds, TimeControlCategory category, boolean rated, String fen, List<String> moves, List<String> san,
                           int ply, String sideToMove, Long drawOfferBy, Outcome result, Termination termination,
                           String terminationLabel, long version, Instant createdAt, Instant finishedAt,
                           Long roomId, Integer boardNo) {

        public static GameView of(Game g, Instant now) {
            return new GameView(g.getId(), g.getStatus(),
                    new Side(g.getWhitePlayerId(), g.getWhiteName(), g.getWhiteRatingBefore(), g.getWhiteRatingAfter(),
                            g.remainingMs(true, now)),
                    new Side(g.getBlackPlayerId(), g.getBlackName(), g.getBlackRatingBefore(), g.getBlackRatingAfter(),
                            g.remainingMs(false, now)),
                    g.getChallengerId(), g.getInitialSeconds(), g.getIncrementSeconds(), g.category(), g.isRated(), g.getFen(),
                    g.uciMoves(), g.sanMoves(), g.ply(), g.whiteToMove() ? "WHITE" : "BLACK", g.getDrawOfferBy(),
                    g.getResult(), g.getTermination(), g.getTermination() == null ? null : g.getTermination().label(),
                    g.getVersion(), g.getCreatedAt(), g.getFinishedAt(), g.getRoomId(), g.getBoardNo());
        }
    }

    /** "Mis partidas": desafíos que me hicieron, los que hice, en curso y las últimas terminadas. */
    public record Mine(List<GameView> incoming, List<GameView> outgoing, List<GameView> active, List<GameView> finished) {}
}
