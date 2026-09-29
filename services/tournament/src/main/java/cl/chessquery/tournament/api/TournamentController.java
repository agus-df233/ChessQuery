package cl.chessquery.tournament.api;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.tournament.FinishService;
import cl.chessquery.tournament.RoundService;
import cl.chessquery.tournament.TournamentQueries;
import cl.chessquery.tournament.TournamentService;
import cl.chessquery.tournament.TrfService;
import cl.chessquery.tournament.api.TournamentDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API autenticada: el organizador dirige sus torneos; el jugador se inscribe y ve los suyos. */
@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService lifecycle;
    private final RoundService roundService;
    private final FinishService finishService;
    private final TrfService trf;
    private final TournamentQueries queries;

    @GetMapping("/mine")
    public Mine mine(@CurrentUser UserPrincipal me) {
        return queries.mine(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@CurrentUser UserPrincipal me, @Valid @RequestBody UpsertRequest req) {
        return lifecycle.create(me, req);
    }

    @PutMapping("/{id}")
    public Detail update(@CurrentUser UserPrincipal me, @PathVariable long id, @Valid @RequestBody UpsertRequest req) {
        return lifecycle.update(me, id, req);
    }

    @PostMapping("/{id}/registrations")
    public Detail register(@CurrentUser UserPrincipal me, @PathVariable long id, @Valid @RequestBody RegisterRequest req) {
        return lifecycle.register(me, id, req.playerId());
    }

    @DeleteMapping("/{id}/registrations/{playerId}")
    public Detail unregister(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable long playerId) {
        return lifecycle.unregister(me, id, playerId);
    }

    @PostMapping("/{id}/join")
    public Detail join(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return lifecycle.join(me, id);
    }

    @DeleteMapping("/{id}/join")
    public Detail leave(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return lifecycle.unregister(me, id, me.playerId());
    }

    @PostMapping("/{id}/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public RoundView nextRound(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return roundService.generate(me, id);
    }

    @PutMapping("/{id}/rounds/{number}/boards/{board}")
    public RoundView result(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable int number,
                            @PathVariable int board, @Valid @RequestBody ResultRequest req) {
        return roundService.record(me, id, number, board, req.result());
    }

    @PostMapping("/{id}/finish")
    public List<StandingView> finish(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return finishService.finish(me, id);
    }

    @GetMapping(value = "/{id}/trf", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> trf(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"torneo-" + id + ".trf\"")
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(trf.export(me, id));
    }
}
