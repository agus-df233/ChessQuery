package cl.chessquery.tournament;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.common.rating.EloCalculator;
import cl.chessquery.common.rating.TimeControlCategory;
import cl.chessquery.tournament.api.TournamentDtos.StandingView;
import cl.chessquery.tournament.domain.Pairing;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.Status;
import cl.chessquery.tournament.domain.Tournament;
import cl.chessquery.tournament.events.TournamentEvents;
import cl.chessquery.tournament.users.UsersClient;
import cl.chessquery.tournament.users.UsersClient.PlayerSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cierre del torneo: exige todos los resultados, fija la tabla y, si el torneo es válido para rating, calcula el
 * nuevo rating de plataforma de cada jugador (ELO con el rating previo al torneo, como la FIDE) y publica un
 * {@code elo.updated} por jugador; users actualiza su rating e historial.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinishService {

    private final TournamentService lifecycle;
    private final TournamentLoader loader;
    private final TournamentQueries queries;
    private final UsersClient users;
    private final EventPublisher events;

    @Transactional
    public List<StandingView> finish(UserPrincipal me, long id) {
        Tournament t = lifecycle.owned(me, id);
        TournamentState s = loader.state(t);
        if (t.getStatus() != Status.IN_PROGRESS) throw ApiException.conflict("NOT_IN_PROGRESS", "El torneo no está en juego");
        if (!s.lastRoundComplete()) {
            throw ApiException.conflict("ROUND_INCOMPLETE", "Faltan resultados de la ronda " + s.currentRound());
        }
        t.setStatus(Status.FINISHED);
        t.setFinishedAt(Instant.now());
        List<StandingView> table = queries.standings(s);
        if (t.isRated()) publishRatings(s);
        events.publish(TournamentEvents.FINISHED, Map.of("tournamentId", id, "name", t.getName(),
                "standings", table.stream().map(r -> Map.of("playerId", r.player().playerId(),
                        "position", r.position(), "points", r.points())).toList()));
        log.info("Torneo {} cerrado ({} jugadores, rating {})", id, table.size(), t.isRated());
        return table;
    }

    /**
     * ELO ChessQuery del ritmo del torneo: parte del rating actual de ese ritmo en users (o, si no tiene, del rating
     * con que se sembró) y publica el nuevo, uno por jugador.
     */
    private void publishRatings(TournamentState s) {
        TimeControlCategory category = s.tournament().category();
        Map<Long, PlayerSummary> current = users.players(s.registrationsById().keySet()).stream()
                .collect(Collectors.toMap(PlayerSummary::id, Function.identity()));
        Map<Long, Integer> before = new HashMap<>();
        s.registrations().forEach(r -> before.put(r.getPlayerId(),
                ratingBefore(r, current.get(r.getPlayerId()), category)));
        Map<Long, List<EloCalculator.Game>> games = gamesByPlayer(s.allPairings(), before);
        games.forEach((playerId, list) -> {
            int old = before.get(playerId);
            boolean unrated = current.get(playerId) == null || current.get(playerId).platformRating(category) == null;
            int next = EloCalculator.next(old, unrated, list);
            events.publish(TournamentEvents.ELO_UPDATED, Map.of("playerId", playerId, "oldElo", old, "newElo", next,
                    "delta", next - old, "ratingType", category.ratingType(), "source", "TOURNAMENT",
                    "tournamentId", s.tournament().getId()));
        });
    }

    private static int ratingBefore(Registration r, PlayerSummary now, TimeControlCategory category) {
        Integer own = now == null ? null : now.platformRating(category);
        return own != null ? own : r.getSeedRating();
    }

    /** Solo partidas jugadas en el tablero; byes y no presentaciones no cambian el rating. */
    static Map<Long, List<EloCalculator.Game>> gamesByPlayer(List<Pairing> pairings, Map<Long, Integer> before) {
        Map<Long, List<EloCalculator.Game>> games = new HashMap<>();
        for (Pairing p : pairings) {
            if (p.getBlackPlayerId() == null || p.getResult() == null || !p.getResult().overTheBoard()) continue;
            Long w = p.getWhitePlayerId();
            Long b = p.getBlackPlayerId();
            if (!before.containsKey(w) || !before.containsKey(b)) continue;
            games.computeIfAbsent(w, k -> new ArrayList<>())
                    .add(new EloCalculator.Game(before.get(b), p.getResult().halfPoints(true) / 2.0));
            games.computeIfAbsent(b, k -> new ArrayList<>())
                    .add(new EloCalculator.Game(before.get(w), p.getResult().halfPoints(false) / 2.0));
        }
        return games;
    }
}
