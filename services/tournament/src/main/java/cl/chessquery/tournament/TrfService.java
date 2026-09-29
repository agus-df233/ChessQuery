package cl.chessquery.tournament;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.tournament.api.TournamentDtos.StandingView;
import cl.chessquery.tournament.domain.Pairing;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.Round;
import cl.chessquery.tournament.domain.Tournament;
import cl.chessquery.tournament.trf.TrfExporter;
import cl.chessquery.tournament.users.UsersClient;
import cl.chessquery.tournament.users.UsersClient.PlayerSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** TRF para homologar: solo el organizador; usa nombres completos y FIDE id desde users (no la foto pública). */
@Service
@RequiredArgsConstructor
public class TrfService {

    private final TournamentService lifecycle;
    private final TournamentLoader loader;
    private final TournamentQueries queries;
    private final UsersClient users;

    @Transactional(readOnly = true)
    public String export(UserPrincipal me, long id) {
        Tournament t = lifecycle.owned(me, id);
        TournamentState s = loader.state(t);
        Map<Long, Integer> rank = s.startRanks();
        Map<Long, PlayerSummary> full = users.players(rank.keySet()).stream()
                .collect(Collectors.toMap(PlayerSummary::id, Function.identity()));
        Map<Long, StandingView> table = queries.standings(s).stream()
                .collect(Collectors.toMap(r -> r.player().playerId(), Function.identity()));
        List<TrfExporter.Entry> entries = s.registrations().stream()
                .map(r -> entry(r, rank.get(r.getPlayerId()), full.get(r.getPlayerId()), table.get(r.getPlayerId())))
                .toList();
        TrfExporter.Header header = new TrfExporter.Header(t.getName(), t.getCity(), "CHI", t.getStartDate(),
                t.getEndDate(), t.getFormat() == cl.chessquery.tournament.domain.Format.SWISS ? "Suizo" : "Round robin",
                t.getTimeControl(), t.getRoundsPlanned());
        return TrfExporter.export(header, entries, boards(s, rank));
    }

    private static TrfExporter.Entry entry(Registration r, int startRank, PlayerSummary p, StandingView row) {
        String last = p != null ? p.lastName() : r.getLastName();
        String first = p != null ? p.firstName() : r.getFirstName();
        return new TrfExporter.Entry(startRank, first, last, p == null ? null : p.gender(), r.getTitle(),
                r.getSeedRating(), p == null ? null : p.fideId(), p == null ? null : p.birthYear(),
                row == null ? 0 : row.points(), row == null ? 0 : row.position());
    }

    private static Map<Integer, List<TrfExporter.Board>> boards(TournamentState s, Map<Long, Integer> rank) {
        Map<Integer, List<TrfExporter.Board>> byRank = new HashMap<>();
        for (Round r : s.rounds()) {
            for (Pairing p : s.pairings(r)) {
                if (!rank.containsKey(p.getWhitePlayerId())) continue;
                Integer black = p.getBlackPlayerId() == null ? null : rank.get(p.getBlackPlayerId());
                TrfExporter.Board b = new TrfExporter.Board(r.getNumber(), rank.get(p.getWhitePlayerId()), black, p.getResult());
                byRank.computeIfAbsent(b.white(), k -> new ArrayList<>()).add(b);
                if (black != null) byRank.computeIfAbsent(black, k -> new ArrayList<>()).add(b);
            }
        }
        return byRank;
    }
}
