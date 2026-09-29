package cl.chessquery.tournament;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.tournament.api.TournamentDtos.RoundView;
import cl.chessquery.tournament.domain.Format;
import cl.chessquery.tournament.domain.Pairing;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.Repositories;
import cl.chessquery.tournament.domain.Result;
import cl.chessquery.tournament.domain.Round;
import cl.chessquery.tournament.domain.Status;
import cl.chessquery.tournament.domain.Tournament;
import cl.chessquery.tournament.events.TournamentEvents;
import cl.chessquery.tournament.pairing.Pair;
import cl.chessquery.tournament.pairing.RoundRobin;
import cl.chessquery.tournament.pairing.SwissPairing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rondas: generar la siguiente (suizo o round robin) y cargar o corregir resultados de la ronda en curso. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundService {

    private final Repositories.Rounds rounds;
    private final Repositories.Pairings pairings;
    private final TournamentService lifecycle;
    private final TournamentLoader loader;
    private final TournamentQueries queries;
    private final EventPublisher events;

    @Transactional
    public RoundView generate(UserPrincipal me, long id) {
        Tournament t = lifecycle.owned(me, id);
        TournamentState state = loader.state(t);
        validateNextRound(state);
        if (t.getStatus() == Status.OPEN && t.getFormat() == Format.ROUND_ROBIN) {
            t.setRoundsPlanned(RoundRobin.rounds(state.registrations().size()));
        }
        int number = state.currentRound() + 1;
        List<Pair> pairs = pairsFor(state, number);
        Round round = new Round();
        round.setTournamentId(id);
        round.setNumber(number);
        rounds.save(round);
        List<Pairing> saved = savePairings(round, pairs);
        t.setStatus(Status.IN_PROGRESS);
        events.publish(TournamentEvents.ROUND_GENERATED, Map.of("tournamentId", id, "round", number,
                "pairings", saved.stream().map(RoundService::eventBoard).toList()));
        log.info("Torneo {}: ronda {} generada ({} mesas)", id, number, saved.size());
        return queries.round(id, number);
    }

    private static void validateNextRound(TournamentState s) {
        Tournament t = s.tournament();
        if (t.getStatus() == Status.FINISHED) throw ApiException.conflict("TOURNAMENT_FINISHED", "El torneo ya terminó");
        if (s.registrations().size() < 2) {
            throw ApiException.conflict("NOT_ENOUGH_PLAYERS", "Se necesitan al menos 2 jugadores inscritos");
        }
        if (!s.lastRoundComplete()) {
            throw ApiException.conflict("ROUND_INCOMPLETE", "Faltan resultados de la ronda " + s.currentRound());
        }
        boolean roundRobinStart = t.getStatus() == Status.OPEN && t.getFormat() == Format.ROUND_ROBIN;
        if (!roundRobinStart && s.currentRound() >= t.getRoundsPlanned()) {
            throw ApiException.conflict("ALL_ROUNDS_PLAYED", "Ya se jugaron las " + t.getRoundsPlanned() + " rondas");
        }
    }

    private static List<Pair> pairsFor(TournamentState s, int number) {
        if (s.tournament().getFormat() == Format.ROUND_ROBIN) {
            return RoundRobin.round(s.registrations().stream().map(Registration::getPlayerId).toList(), number);
        }
        try {
            return SwissPairing.pair(s.competitors(), number);
        } catch (IllegalStateException e) {
            throw ApiException.conflict("NO_PAIRING_POSSIBLE", "No hay pareo sin repetir rivales: cierra el torneo");
        }
    }

    private List<Pairing> savePairings(Round round, List<Pair> pairs) {
        List<Pairing> saved = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            Pairing p = new Pairing();
            p.setRoundId(round.getId());
            p.setBoard(i + 1);
            p.setWhitePlayerId(pairs.get(i).white());
            p.setBlackPlayerId(pairs.get(i).black());
            p.setResult(pairs.get(i).isBye() ? Result.BYE : null);
            saved.add(pairings.save(p));
        }
        return saved;
    }

    private static Map<String, Object> eventBoard(Pairing p) {
        Map<String, Object> board = new java.util.HashMap<>();
        board.put("board", p.getBoard());
        board.put("whitePlayerId", p.getWhitePlayerId());
        board.put("blackPlayerId", p.getBlackPlayerId());
        return board;
    }

    /** Resultado de una mesa. Solo en la última ronda generada (corregir rondas anteriores cambiaría pareos ya hechos). */
    @Transactional
    public RoundView record(UserPrincipal me, long id, int number, int board, Result result) {
        Tournament t = lifecycle.owned(me, id);
        if (t.getStatus() != Status.IN_PROGRESS) throw ApiException.conflict("NOT_IN_PROGRESS", "El torneo no está en juego");
        Round last = rounds.findByTournamentIdOrderByNumberAsc(id).stream().reduce((a, b) -> b).orElseThrow();
        if (last.getNumber() != number) {
            throw ApiException.conflict("ROUND_CLOSED", "Solo se pueden cargar resultados de la ronda " + last.getNumber());
        }
        Pairing p = pairings.findByRoundIdAndBoard(last.getId(), board)
                .orElseThrow(() -> ApiException.notFound("BOARD_NOT_FOUND", "Mesa " + board + " no existe"));
        if (p.getBlackPlayerId() == null || result == Result.BYE) {
            throw ApiException.badRequest("INVALID_RESULT", "El bye no lleva resultado");
        }
        p.setResult(result);
        return queries.round(id, number);
    }
}
