package cl.chessquery.tournament;

import cl.chessquery.tournament.domain.Pairing;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.Round;
import cl.chessquery.tournament.domain.Tournament;
import cl.chessquery.tournament.pairing.Competitor;
import cl.chessquery.tournament.standings.Standings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Foto completa de un torneo (inscritos en orden de siembra, rondas y mesas) para calcular pareos, tabla y TRF
 * sin volver a la base de datos.
 */
public record TournamentState(Tournament tournament, List<Registration> registrations, List<Round> rounds,
                              Map<Long, List<Pairing>> pairingsByRound) {

    public int currentRound() {
        return rounds.size();
    }

    public List<Pairing> pairings(Round r) {
        return pairingsByRound.getOrDefault(r.getId(), List.of());
    }

    public List<Pairing> allPairings() {
        return rounds.stream().flatMap(r -> pairings(r).stream()).toList();
    }

    public boolean lastRoundComplete() {
        return rounds.isEmpty() || pairings(rounds.get(rounds.size() - 1)).stream().allMatch(p -> p.getResult() != null);
    }

    public Map<Long, Registration> registrationsById() {
        return registrations.stream().collect(Collectors.toMap(Registration::getPlayerId, Function.identity(),
                (a, b) -> a, LinkedHashMap::new));
    }

    /** Número inicial (1 = mayor rating al inscribirse). */
    public Map<Long, Integer> startRanks() {
        Map<Long, Integer> ranks = new HashMap<>();
        for (int i = 0; i < registrations.size(); i++) ranks.put(registrations.get(i).getPlayerId(), i + 1);
        return ranks;
    }

    public List<Standings.Game> games() {
        return allPairings().stream()
                .map(p -> new Standings.Game(p.getWhitePlayerId(), p.getBlackPlayerId(), p.getResult())).toList();
    }

    public Map<Long, Integer> seedRatings() {
        Map<Long, Integer> ratings = new LinkedHashMap<>();
        registrations.forEach(r -> ratings.put(r.getPlayerId(), r.getSeedRating()));
        return ratings;
    }

    /** Estado de cada inscrito para el pareo suizo: puntos, rivales, colores jugados y bye. */
    public List<Competitor> competitors() {
        Map<Long, int[]> half = new HashMap<>();
        Map<Long, List<Long>> rivals = new HashMap<>();
        Map<Long, List<Boolean>> colors = new HashMap<>();
        Map<Long, Boolean> byes = new HashMap<>();
        for (Registration r : registrations) {
            half.put(r.getPlayerId(), new int[1]);
            rivals.put(r.getPlayerId(), new ArrayList<>());
            colors.put(r.getPlayerId(), new ArrayList<>());
        }
        for (Pairing p : allPairings()) {
            accumulate(p, half, rivals, colors, byes);
        }
        return registrations.stream().map(r -> new Competitor(r.getPlayerId(), r.getSeedRating(),
                half.get(r.getPlayerId())[0], rivals.get(r.getPlayerId()), colors.get(r.getPlayerId()),
                byes.getOrDefault(r.getPlayerId(), false))).toList();
    }

    private static void accumulate(Pairing p, Map<Long, int[]> half, Map<Long, List<Long>> rivals,
                                   Map<Long, List<Boolean>> colors, Map<Long, Boolean> byes) {
        long w = p.getWhitePlayerId();
        if (p.getBlackPlayerId() == null) {
            byes.put(w, true);
            if (half.containsKey(w)) half.get(w)[0] += 2;
            return;
        }
        long b = p.getBlackPlayerId();
        if (!half.containsKey(w) || !half.containsKey(b)) return; // jugador retirado: no altera el pareo
        rivals.get(w).add(b);
        rivals.get(b).add(w);
        if (p.getResult() == null) return;
        half.get(w)[0] += p.getResult().halfPoints(true);
        half.get(b)[0] += p.getResult().halfPoints(false);
        if (p.getResult().overTheBoard()) {
            colors.get(w).add(true);
            colors.get(b).add(false);
        }
    }
}
