package cl.chessquery.game.api;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.game.GameQueries;
import cl.chessquery.game.GameService;
import cl.chessquery.game.api.GameDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

/** Partidas del jugador autenticado: desafíos, jugadas, abandono y tablas. */
@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService games;
    private final GameQueries queries;

    @GetMapping("/mine")
    public Mine mine(@CurrentUser UserPrincipal me) {
        return queries.mine(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameView challenge(@CurrentUser UserPrincipal me, @Valid @RequestBody ChallengeRequest req) {
        return games.challenge(me, req);
    }

    @GetMapping(value = "/{id}", params = "!afterVersion")
    public GameView get(@PathVariable long id) {
        return queries.view(id);
    }

    /** Long polling: responde cuando la partida pase de {@code afterVersion} (o a los 25 s con el estado actual). */
    @GetMapping(value = "/{id}", params = "afterVersion")
    public DeferredResult<GameView> watch(@PathVariable long id, @RequestParam long afterVersion) {
        return queries.watch(id, afterVersion);
    }

    @PostMapping("/{id}/accept")
    public GameView accept(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return games.accept(me, id);
    }

    @PostMapping("/{id}/decline")
    public GameView decline(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return games.decline(me, id);
    }

    @PostMapping("/{id}/cancel")
    public GameView cancel(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return games.cancel(me, id);
    }

    @PostMapping("/{id}/moves")
    public GameView move(@CurrentUser UserPrincipal me, @PathVariable long id, @Valid @RequestBody MoveRequest req) {
        return games.move(me, id, req.uci());
    }

    @PostMapping("/{id}/resign")
    public GameView resign(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return games.resign(me, id);
    }

    @PostMapping("/{id}/draw/offer")
    public GameView offerDraw(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return games.offerDraw(me, id);
    }

    @PostMapping("/{id}/draw/accept")
    public GameView acceptDraw(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return games.acceptDraw(me, id);
    }

    @PostMapping("/{id}/draw/decline")
    public GameView declineDraw(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return games.declineDraw(me, id);
    }
}
