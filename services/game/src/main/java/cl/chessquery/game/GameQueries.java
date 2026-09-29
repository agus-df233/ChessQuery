package cl.chessquery.game;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.game.api.GameDtos.GameView;
import cl.chessquery.game.api.GameDtos.Mine;
import cl.chessquery.game.domain.Game;
import cl.chessquery.game.domain.GameRepository;
import cl.chessquery.game.domain.GameStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Lecturas de partidas y long polling. */
@Service
@RequiredArgsConstructor
public class GameQueries {

    private final GameRepository games;
    private final GameNotifier notifier;
    private final Clock clock;

    @Value("${chessquery.games.long-poll-timeout:25s}")
    private Duration longPollTimeout;

    @Transactional(readOnly = true)
    public GameView view(long id) {
        return GameView.of(require(id), clock.instant());
    }

    /**
     * Long polling: responde apenas la partida tenga una versión mayor que {@code afterVersion}; si no hay cambios
     * en {@code long-poll-timeout}, responde el estado actual (el cliente vuelve a preguntar). Una partida
     * terminada responde de inmediato.
     */
    public DeferredResult<GameView> watch(long id, long afterVersion) {
        DeferredResult<GameView> result = new DeferredResult<>(longPollTimeout.toMillis());
        GameView now = view(id);
        if (now.version() > afterVersion || now.status().isOver()) {
            result.setResult(now);
            return result;
        }
        Runnable onChange = () -> result.setResult(view(id));
        notifier.await(id, onChange);
        result.onTimeout(() -> result.setResult(view(id)));
        result.onCompletion(() -> notifier.forget(id, onChange));
        GameView again = view(id); // por si cambió entre la primera lectura y el registro
        if (again.version() > afterVersion) result.setResult(again);
        return result;
    }

    @Transactional(readOnly = true)
    public Mine mine(UserPrincipal me) {
        long p = me.playerId();
        List<Game> pending = games.findByPlayerAndStatus(p, List.of(GameStatus.PENDING));
        return new Mine(
                views(pending.stream().filter(g -> g.getChallengerId() != p).toList()),
                views(pending.stream().filter(g -> g.getChallengerId() == p).toList()),
                views(games.findByPlayerAndStatus(p, List.of(GameStatus.ACTIVE))),
                views(games.findByPlayerAndStatus(p, List.of(GameStatus.FINISHED)).stream().limit(20).toList()));
    }

    @Transactional(readOnly = true)
    public List<GameView> recentFinished() {
        return views(games.findTop20ByStatusOrderByFinishedAtDesc(GameStatus.FINISHED));
    }

    @Transactional(readOnly = true)
    public String pgn(long id) {
        Game g = require(id);
        if (g.getPgn() == null) throw ApiException.conflict("GAME_NOT_FINISHED", "La partida aún no termina");
        return g.getPgn();
    }

    private List<GameView> views(List<Game> list) {
        return list.stream().map(g -> GameView.of(g, clock.instant())).toList();
    }

    private Game require(long id) {
        return games.findById(id).orElseThrow(() -> ApiException.notFound("GAME_NOT_FOUND", "Partida " + id + " no encontrada"));
    }
}
