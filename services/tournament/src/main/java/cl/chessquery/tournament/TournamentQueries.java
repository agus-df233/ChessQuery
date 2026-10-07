package cl.chessquery.tournament;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.tournament.api.TournamentDtos.*;
import cl.chessquery.tournament.domain.FederationTournament;
import cl.chessquery.tournament.domain.Pairing;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.Repositories;
import cl.chessquery.tournament.domain.Round;
import cl.chessquery.tournament.domain.Status;
import cl.chessquery.tournament.domain.Tournament;
import cl.chessquery.tournament.standings.Standings;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lecturas: detalle, rondas, tabla, listados y calendario de la Federación. Sin datos personales. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentQueries {

    private final Repositories.Tournaments tournaments;
    private final Repositories.Registrations registrations;
    private final Repositories.Rounds rounds;
    private final Repositories.FederationTournaments federation;
    private final TournamentLoader loader;
    private final TournamentLive notifier;

    @Value("${chessquery.tournaments.long-poll-timeout:25s}")
    private Duration longPollTimeout;

    public Detail detail(long id) {
        return detail(loader.state(id));
    }

    private Detail detail(TournamentState s) {
        List<EntryView> players = new ArrayList<>();
        for (int i = 0; i < s.registrations().size(); i++) {
            Registration r = s.registrations().get(i);
            players.add(new EntryView(i + 1, PlayerRef.of(r), r.getClubName(), r.getWithdrawnFromRound()));
        }
        return new Detail(view(s), players);
    }

    public List<RoundView> rounds(long id) {
        return rounds(loader.state(id));
    }

    private List<RoundView> rounds(TournamentState s) {
        Map<Long, Registration> regs = s.registrationsById();
        return s.rounds().stream().map(r -> roundView(r, s.pairings(r), regs)).toList();
    }

    public RoundView round(long id, int number) {
        return rounds(id).stream().filter(r -> r.number() == number).findFirst()
                .orElseThrow(() -> ApiException.notFound("ROUND_NOT_FOUND", "Ronda " + number + " no existe"));
    }

    private static RoundView roundView(Round r, List<Pairing> pairings, Map<Long, Registration> regs) {
        List<BoardView> boards = pairings.stream().map(p -> new BoardView(p.getBoard(),
                PlayerRef.of(regs.get(p.getWhitePlayerId())),
                p.getBlackPlayerId() == null ? null : PlayerRef.of(regs.get(p.getBlackPlayerId())),
                p.getResult(), p.getResult() == null ? null : p.getResult().label())).toList();
        return new RoundView(r.getNumber(), pairings.stream().allMatch(p -> p.getResult() != null), boards);
    }

    public List<StandingView> standings(long id) {
        return standings(loader.state(id));
    }

    List<StandingView> standings(TournamentState s) {
        Map<Long, Registration> regs = s.registrationsById();
        return Standings.compute(s.seedRatings(), s.games(), s.tournament().getFormat()).stream()
                .map(row -> new StandingView(row.position(), PlayerRef.of(regs.get(row.playerId())), row.points(),
                        row.buchholzCut1(), row.buchholz(), row.sonnebornBerger(), row.wins(), row.played()))
                .toList();
    }

    /** Todo lo de la pantalla de la sala con su versión. La versión se lee primero: si algo cambia mientras se arma la
     * vista, el cliente recibe datos nuevos con versión vieja y su siguiente consulta vuelve de inmediato (inocuo). */
    public LiveView live(long id) {
        long version = tournaments.findVersionById(id)
                .orElseThrow(() -> ApiException.notFound("TOURNAMENT_NOT_FOUND", "Torneo " + id + " no encontrado"));
        TournamentState s = loader.state(id);
        List<StandingView> table = s.tournament().getStatus() == Status.OPEN ? List.of() : standings(s);
        return new LiveView(version, detail(s), rounds(s), table);
    }

    /** Long polling: responde apenas el torneo pase de {@code afterVersion}, o a los 25 s con el estado actual. */
    public DeferredResult<LiveView> watch(long id, long afterVersion) {
        DeferredResult<LiveView> result = new DeferredResult<>(longPollTimeout.toMillis());
        LiveView now = live(id);
        if (now.version() > afterVersion) {
            result.setResult(now);
            return result;
        }
        Runnable onChange = () -> result.setResult(live(id));
        notifier.await(id, onChange);
        result.onTimeout(() -> result.setResult(live(id)));
        result.onCompletion(() -> notifier.forget(id, onChange));
        LiveView again = live(id); // por si cambió entre la primera lectura y el registro
        if (again.version() > afterVersion) result.setResult(again);
        return result;
    }

    /** Listado público: abiertos y en juego primero (por fecha), luego los terminados. */
    public List<TournamentView> list(Status status) {
        List<Status> filter = status == null ? List.of(Status.OPEN, Status.IN_PROGRESS, Status.FINISHED) : List.of(status);
        return tournaments.findByStatusInOrderByStartDateAsc(filter).stream().map(this::view).toList();
    }

    public Mine mine(UserPrincipal me) {
        List<TournamentView> organized = tournaments.findByOrganizerIdOrderByStartDateDesc(me.playerId()).stream()
                .map(this::view).toList();
        List<Long> ids = registrations.findByPlayerId(me.playerId()).stream().map(Registration::getTournamentId).toList();
        List<TournamentView> registered = ids.isEmpty() ? List.of()
                : tournaments.findByIdInOrderByStartDateDesc(ids).stream().map(this::view).toList();
        return new Mine(organized, registered);
    }

    public List<FederationTournament> calendar() {
        return federation.findByStartDateGreaterThanEqualOrderByStartDateAsc(LocalDate.now().minusDays(7));
    }

    private TournamentView view(Tournament t) {
        return TournamentView.of(t, rounds.findByTournamentIdOrderByNumberAsc(t.getId()).size(),
                registrations.findByTournamentIdOrderBySeedRatingDescIdAsc(t.getId()));
    }

    private TournamentView view(TournamentState s) {
        return TournamentView.of(s.tournament(), s.currentRound(),
                registrations.findByTournamentIdOrderBySeedRatingDescIdAsc(s.tournament().getId()));
    }
}
