package cl.chessquery.tournament.api;

import cl.chessquery.tournament.TournamentQueries;
import cl.chessquery.tournament.api.TournamentDtos.*;
import cl.chessquery.tournament.domain.FederationTournament;
import cl.chessquery.tournament.domain.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Vista pública (sin login, se comparte por QR): torneos, inscritos, rondas, tabla y calendario federativo. */
@RestController
@RequestMapping("/api/public/tournaments")
@RequiredArgsConstructor
public class PublicTournamentController {

    private final TournamentQueries queries;

    @GetMapping
    public List<TournamentView> list(@RequestParam(required = false) Status status) {
        return queries.list(status);
    }

    @GetMapping("/calendar")
    public List<FederationTournament> calendar() {
        return queries.calendar();
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable long id) {
        return queries.detail(id);
    }

    @GetMapping("/{id}/rounds")
    public List<RoundView> rounds(@PathVariable long id) {
        return queries.rounds(id);
    }

    @GetMapping("/{id}/standings")
    public List<StandingView> standings(@PathVariable long id) {
        return queries.standings(id);
    }
}
