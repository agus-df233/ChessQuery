package cl.chessquery.tournament.api;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.tournament.FinishService;
import cl.chessquery.tournament.RegistrationService;
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
    private final RegistrationService registrations;
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
        return registrations.register(me, id, req.playerId());
    }

    @DeleteMapping("/{id}/registrations/{playerId}")
    public Detail unregister(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable long playerId) {
        return registrations.unregister(me, id, playerId);
    }

    @PostMapping("/{id}/join")
    public Detail join(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return registrations.join(me, id);
    }

    @DeleteMapping("/{id}/join")
    public Detail leave(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return registrations.unregister(me, id, me.playerId());
    }

    /** Todas las inscripciones con estado y código de acreditación (solo el organizador). */
    @GetMapping("/{id}/registrations")
    public List<RegistrationView> registrations(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return registrations.list(me, id);
    }

    @PostMapping("/{id}/registrations/bulk")
    public List<BulkRow> registerAll(@CurrentUser UserPrincipal me, @PathVariable long id, @Valid @RequestBody BulkRegisterRequest req) {
        return registrations.registerAll(me, id, req.playerIds());
    }

    @PostMapping("/{id}/registrations/{playerId}/approve")
    public Detail approve(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable long playerId) {
        return registrations.approve(me, id, playerId);
    }

    /** Durante el torneo: deja de emparejarse desde la ronda siguiente. */
    @PostMapping("/{id}/registrations/{playerId}/withdraw")
    public Detail withdraw(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable long playerId) {
        return registrations.withdraw(me, id, playerId);
    }

    /** Acreditación con el QR del jugador. */
    @PostMapping("/{id}/checkin")
    public CheckinResult checkin(@CurrentUser UserPrincipal me, @PathVariable long id, @Valid @RequestBody CheckinRequest req) {
        return registrations.checkinByCode(me, id, req.code());
    }

    @PostMapping("/{id}/registrations/{playerId}/checkin")
    public CheckinResult checkinManually(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable long playerId) {
        return registrations.checkinManually(me, id, playerId);
    }

    @DeleteMapping("/{id}/registrations/{playerId}/checkin")
    public RegistrationView undoCheckin(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable long playerId) {
        return registrations.undoCheckin(me, id, playerId);
    }

    /** Mi inscripción: estado y código de mi QR de acreditación. */
    @GetMapping("/{id}/my-registration")
    public RegistrationView myRegistration(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return registrations.mine(me, id);
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
