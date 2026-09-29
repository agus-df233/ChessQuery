package cl.chessquery.tournament;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.tournament.domain.Pairing;
import cl.chessquery.tournament.domain.Repositories;
import cl.chessquery.tournament.domain.Round;
import cl.chessquery.tournament.domain.Tournament;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Carga torneos y su estado completo; centraliza el 404. */
@Component
@RequiredArgsConstructor
public class TournamentLoader {

    private final Repositories.Tournaments tournaments;
    private final Repositories.Registrations registrations;
    private final Repositories.Rounds rounds;
    private final Repositories.Pairings pairings;

    public Tournament tournament(long id) {
        return tournaments.findById(id)
                .orElseThrow(() -> ApiException.notFound("TOURNAMENT_NOT_FOUND", "Torneo " + id + " no encontrado"));
    }

    public TournamentState state(long id) {
        return state(tournament(id));
    }

    public TournamentState state(Tournament t) {
        List<Round> rs = rounds.findByTournamentIdOrderByNumberAsc(t.getId());
        Map<Long, List<Pairing>> byRound = rs.isEmpty() ? Map.of()
                : pairings.findByRoundIdInOrderByBoardAsc(rs.stream().map(Round::getId).toList()).stream()
                        .collect(Collectors.groupingBy(Pairing::getRoundId));
        return new TournamentState(t, registrations.findByTournamentIdOrderBySeedRatingDescIdAsc(t.getId()), rs, byRound);
    }
}
