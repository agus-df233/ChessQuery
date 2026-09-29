package cl.chessquery.game.api;

import cl.chessquery.game.GameQueries;
import cl.chessquery.game.api.GameDtos.GameView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

/** Espectadores sin login: partida en vivo (solo lectura), últimas terminadas y descarga del PGN. */
@RestController
@RequestMapping("/api/public/games")
@RequiredArgsConstructor
public class PublicGameController {

    private final GameQueries queries;

    @GetMapping
    public List<GameView> recent() {
        return queries.recentFinished();
    }

    @GetMapping(value = "/{id}", params = "!afterVersion")
    public GameView get(@PathVariable long id) {
        return queries.view(id);
    }

    @GetMapping(value = "/{id}", params = "afterVersion")
    public DeferredResult<GameView> watch(@PathVariable long id, @RequestParam long afterVersion) {
        return queries.watch(id, afterVersion);
    }

    @GetMapping(value = "/{id}/pgn", produces = "application/x-chess-pgn")
    public ResponseEntity<String> pgn(@PathVariable long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"partida-" + id + ".pgn\"")
                .contentType(MediaType.parseMediaType("application/x-chess-pgn;charset=UTF-8"))
                .body(queries.pgn(id));
    }
}
