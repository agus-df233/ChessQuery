package cl.chessquery.game;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.game.api.GameDtos.ChallengeRequest;
import cl.chessquery.game.api.GameDtos.ColorChoice;
import cl.chessquery.game.api.GameDtos.GameView;
import cl.chessquery.game.domain.Game;
import cl.chessquery.game.domain.GameRepository;
import cl.chessquery.game.domain.GameStatus;
import cl.chessquery.game.domain.Outcome;
import cl.chessquery.game.domain.Termination;
import cl.chessquery.game.rules.ChessRules;
import cl.chessquery.game.users.UsersClient;
import cl.chessquery.game.users.UsersClient.PlayerSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ciclo de vida de una partida: desafío → aceptación → jugadas con reloj del servidor → fin (mate, tablas,
 * abandono o tiempo). Cada cambio sube {@code version} y despierta a los long polls tras el commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository games;
    private final UsersClient users;
    private final GameFinisher finisher;
    private final GameNotifier notifier;
    private final Clock clock;

    @Transactional
    public GameView challenge(UserPrincipal me, ChallengeRequest req) {
        if (req.opponentId() == me.playerId()) throw ApiException.badRequest("SELF_CHALLENGE", "No puedes desafiarte a ti mismo");
        PlayerSummary challenger = users.player(me.playerId());
        PlayerSummary opponent = users.player(req.opponentId());
        if (!opponent.hasAccount()) {
            throw ApiException.conflict("OPPONENT_WITHOUT_ACCOUNT", "Ese jugador aún no tiene cuenta en ChessQuery");
        }
        boolean challengerWhite = challengerIsWhite(req.color());
        Game g = new Game();
        g.setChallengerId(me.playerId());
        assign(g, challengerWhite ? challenger : opponent, challengerWhite ? opponent : challenger);
        g.setInitialSeconds(req.minutes() * 60);
        g.setIncrementSeconds(req.incrementSeconds());
        g.setRated(req.rated() == null || req.rated());
        g.setFen(ChessRules.INITIAL_FEN);
        g.setWhiteMs(g.getInitialSeconds() * 1000L);
        g.setBlackMs(g.getInitialSeconds() * 1000L);
        g.setCreatedAt(clock.instant()); // el vencimiento del desafío (GameSweeper) se mide con este mismo reloj
        games.save(g);
        log.info("Jugador {} desafió a {} (partida {})", me.playerId(), req.opponentId(), g.getId());
        return view(g);
    }

    private static boolean challengerIsWhite(ColorChoice color) {
        if (color == ColorChoice.WHITE) return true;
        if (color == ColorChoice.BLACK) return false;
        return ThreadLocalRandom.current().nextBoolean();
    }

    private static void assign(Game g, PlayerSummary white, PlayerSummary black) {
        g.setWhitePlayerId(white.id());
        g.setWhiteName(white.publicName());
        g.setWhiteRatingBefore(white.startingRating());
        g.setBlackPlayerId(black.id());
        g.setBlackName(black.publicName());
        g.setBlackRatingBefore(black.startingRating());
    }

    /** El desafiado acepta: empieza la partida y corre el reloj de blancas. */
    @Transactional
    public GameView accept(UserPrincipal me, long id) {
        Game g = pendingFor(me, id, false);
        refreshRatings(g);
        Instant now = clock.instant();
        g.setStatus(GameStatus.ACTIVE);
        g.setStartedAt(now);
        g.setTurnStartedAt(now);
        return changed(g);
    }

    /** Ratings al momento de empezar (pudieron cambiar desde el desafío). */
    private void refreshRatings(Game g) {
        PlayerSummary white = users.player(g.getWhitePlayerId());
        PlayerSummary black = users.player(g.getBlackPlayerId());
        g.setWhiteRatingBefore(white.startingRating());
        g.setWhiteUnrated(white.eloPlatform() == null);
        g.setBlackRatingBefore(black.startingRating());
        g.setBlackUnrated(black.eloPlatform() == null);
    }

    @Transactional
    public GameView decline(UserPrincipal me, long id) {
        Game g = pendingFor(me, id, false);
        g.setStatus(GameStatus.DECLINED);
        return changed(g);
    }

    @Transactional
    public GameView cancel(UserPrincipal me, long id) {
        Game g = pendingFor(me, id, true);
        g.setStatus(GameStatus.CANCELLED);
        return changed(g);
    }

    private Game pendingFor(UserPrincipal me, long id, boolean asChallenger) {
        Game g = participant(me, id);
        if (g.getStatus() != GameStatus.PENDING) throw ApiException.conflict("NOT_PENDING", "Ese desafío ya no está pendiente");
        if ((g.getChallengerId() == me.playerId()) != asChallenger) {
            throw ApiException.forbidden("NOT_ALLOWED", asChallenger ? "Solo quien desafió puede cancelar" : "Solo el desafiado puede responder");
        }
        return g;
    }

    /**
     * Jugada del que tiene el turno. Primero se descuenta su tiempo: si se le acabó, pierde por tiempo (o tablas si
     * el rival no puede dar mate) y la jugada no se aplica. Si no, suma el incremento y pasa el turno.
     */
    @Transactional
    public GameView move(UserPrincipal me, long id, String uci) {
        Game g = active(me, id);
        boolean white = g.isWhite(me.playerId());
        if (white != g.whiteToMove()) throw ApiException.conflict("NOT_YOUR_TURN", "No es tu turno");
        Instant now = clock.instant();
        long left = g.remainingMs(white, now);
        if (left <= 0) {
            timeout(g, white, now);
            return changed(g);
        }
        ChessRules.Played played;
        try {
            played = ChessRules.play(g.uciMoves(), uci);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("ILLEGAL_MOVE", e.getMessage());
        }
        applyMove(g, played, white, left, now);
        if (played.ended()) finisher.finish(g, played.outcome(), played.termination(), now);
        return changed(g);
    }

    private void applyMove(Game g, ChessRules.Played played, boolean white, long left, Instant now) {
        long withIncrement = left + g.getIncrementSeconds() * 1000L;
        if (white) g.setWhiteMs(withIncrement);
        else g.setBlackMs(withIncrement);
        g.setMovesUci((g.getMovesUci() + " " + played.uci()).trim());
        g.setMovesSan((g.getMovesSan() + " " + played.san()).trim());
        g.setFen(played.fen());
        g.setTurnStartedAt(now);
        // Jugar en vez de responder rechaza las tablas que ofreció el rival (las propias siguen en pie)
        long mover = white ? g.getWhitePlayerId() : g.getBlackPlayerId();
        if (g.getDrawOfferBy() != null && g.getDrawOfferBy() != mover) g.setDrawOfferBy(null);
    }

    /** Se le acabó el tiempo a {@code white}: gana el rival salvo que no pueda dar mate (tablas, FIDE 6.9). */
    void timeout(Game g, boolean white, Instant now) {
        if (white) g.setWhiteMs(0);
        else g.setBlackMs(0);
        Outcome outcome = ChessRules.canMate(g.uciMoves(), !white) ? Outcome.winner(!white) : Outcome.DRAW;
        finisher.finish(g, outcome, Termination.TIMEOUT, now);
    }

    @Transactional
    public GameView resign(UserPrincipal me, long id) {
        Game g = active(me, id);
        finisher.finish(g, Outcome.winner(!g.isWhite(me.playerId())), Termination.RESIGNATION, clock.instant());
        return changed(g);
    }

    @Transactional
    public GameView offerDraw(UserPrincipal me, long id) {
        Game g = active(me, id);
        if (g.getDrawOfferBy() != null && g.getDrawOfferBy() != me.playerId()) return acceptDraw(me, id);
        g.setDrawOfferBy(me.playerId());
        return changed(g);
    }

    @Transactional
    public GameView acceptDraw(UserPrincipal me, long id) {
        Game g = active(me, id);
        if (g.getDrawOfferBy() == null || g.getDrawOfferBy() == me.playerId()) {
            throw ApiException.conflict("NO_DRAW_OFFER", "Tu rival no ha ofrecido tablas");
        }
        finisher.finish(g, Outcome.DRAW, Termination.AGREEMENT, clock.instant());
        return changed(g);
    }

    @Transactional
    public GameView declineDraw(UserPrincipal me, long id) {
        Game g = active(me, id);
        if (g.getDrawOfferBy() != null && g.getDrawOfferBy() != me.playerId()) g.setDrawOfferBy(null);
        return changed(g);
    }

    private Game active(UserPrincipal me, long id) {
        Game g = participant(me, id);
        if (g.getStatus() != GameStatus.ACTIVE) throw ApiException.conflict("NOT_ACTIVE", "La partida no está en juego");
        return g;
    }

    private Game participant(UserPrincipal me, long id) {
        Game g = games.findById(id).orElseThrow(() -> ApiException.notFound("GAME_NOT_FOUND", "Partida " + id + " no encontrada"));
        if (!g.plays(me.playerId())) throw ApiException.forbidden("NOT_A_PLAYER", "No juegas esta partida");
        return g;
    }

    private GameView changed(Game g) {
        games.saveAndFlush(g);
        notifier.changedAfterCommit(g.getId());
        return view(g);
    }

    private GameView view(Game g) {
        return GameView.of(g, clock.instant());
    }
}
