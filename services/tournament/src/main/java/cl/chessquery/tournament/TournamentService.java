package cl.chessquery.tournament;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.tournament.api.TournamentDtos.Detail;
import cl.chessquery.tournament.api.TournamentDtos.UpsertRequest;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.Repositories;
import cl.chessquery.tournament.domain.Status;
import cl.chessquery.tournament.domain.Tournament;
import cl.chessquery.tournament.users.UsersClient;
import cl.chessquery.tournament.users.UsersClient.PlayerSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ciclo de vida del torneo antes de jugarse: crear y editar (organizador del club), inscripciones (el organizador
 * inscribe a cualquier jugador; un jugador puede inscribirse solo) y retiros mientras las inscripciones están abiertas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentService {

    private static final List<Status> ACTIVE = List.of(Status.OPEN, Status.IN_PROGRESS);

    private final Repositories.Tournaments tournaments;
    private final Repositories.Registrations registrations;
    private final TournamentLoader loader;
    private final TournamentQueries queries;
    private final UsersClient users;

    @Transactional
    public Detail create(UserPrincipal me, UpsertRequest req) {
        UsersClient.PlanInfo plan = users.planOf(me.playerId());
        if (plan.organizationId() == null) {
            throw ApiException.forbidden("NOT_ORGANIZER", "Crea tu club para organizar torneos");
        }
        int max = plan.maxActiveTournaments();
        if (tournaments.countByOrganizationIdAndStatusIn(plan.organizationId(), ACTIVE) >= max) {
            throw ApiException.conflict("PLAN_LIMIT", "Tu plan permite " + max + " torneos activos a la vez");
        }
        Tournament t = new Tournament();
        t.setOrganizationId(plan.organizationId());
        t.setOrganizerId(me.playerId());
        apply(t, req);
        tournaments.save(t);
        log.info("Club {} creó el torneo {} ({})", plan.organizationId(), t.getId(), t.getFormat());
        return queries.detail(t.getId());
    }

    @Transactional
    public Detail update(UserPrincipal me, long id, UpsertRequest req) {
        Tournament t = owned(me, id);
        requireOpen(t);
        apply(t, req);
        return queries.detail(id);
    }

    private static void apply(Tournament t, UpsertRequest req) {
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw ApiException.badRequest("INVALID_DATES", "La fecha de término es anterior a la de inicio");
        }
        t.setName(req.name().trim());
        t.setCity(req.city());
        t.setRegion(req.region());
        t.setStartDate(req.startDate());
        t.setEndDate(req.endDate());
        t.setFormat(req.format());
        t.setRoundsPlanned(req.rounds());
        applyTimeControl(t, req);
        t.setRated(req.rated() == null || req.rated());
    }

    /**
     * Ritmo estructurado: el de los campos {@code baseMinutes}/{@code incrementSeconds} o, si no vienen (clientes
     * antiguos), el que se lea de la etiqueta "90+30". Sin etiqueta, se genera desde los campos.
     */
    private static void applyTimeControl(Tournament t, UpsertRequest req) {
        int[] parsed = req.baseMinutes() != null
                ? new int[] {req.baseMinutes(), req.incrementSeconds() == null ? 0 : req.incrementSeconds()}
                : TimeControlLabel.parse(req.timeControl());
        t.setBaseMinutes(parsed == null ? null : parsed[0]);
        t.setIncrementSeconds(parsed == null ? null : parsed[1]);
        boolean blank = req.timeControl() == null || req.timeControl().isBlank();
        t.setTimeControl(blank && parsed != null ? parsed[0] + "+" + parsed[1] : req.timeControl());
    }

    /** El organizador inscribe a un jugador (de su roster o registrado). */
    @Transactional
    public Detail register(UserPrincipal me, long id, long playerId) {
        Tournament t = owned(me, id);
        enroll(t, users.player(playerId));
        return queries.detail(id);
    }

    /** El jugador se inscribe a sí mismo en un torneo abierto. */
    @Transactional
    public Detail join(UserPrincipal me, long id) {
        enroll(loader.tournament(id), users.player(me.playerId()));
        return queries.detail(id);
    }

    @Transactional
    public Detail unregister(UserPrincipal me, long id, long playerId) {
        Tournament t = loader.tournament(id);
        if (!t.isOwnedBy(me.playerId()) && me.playerId() != playerId) {
            throw ApiException.forbidden("NOT_ALLOWED", "Solo el organizador o el propio jugador pueden retirar la inscripción");
        }
        requireOpen(t);
        registrations.findByTournamentIdAndPlayerId(id, playerId).ifPresent(registrations::delete);
        return queries.detail(id);
    }

    private void enroll(Tournament t, PlayerSummary p) {
        requireOpen(t);
        if (registrations.findByTournamentIdAndPlayerId(t.getId(), p.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_REGISTERED", "Ese jugador ya está inscrito");
        }
        Registration r = new Registration();
        r.setTournamentId(t.getId());
        r.setPlayerId(p.id());
        r.setFirstName(p.firstName());
        r.setLastName(p.publicLastName() == null ? "" : p.publicLastName());
        r.setTitle(p.currentTitle());
        r.setClubName(p.clubName());
        r.setSeedRating(p.seedRating(t.category()));
        r.setPlatformRating(p.platformRating(t.category()));
        registrations.save(r);
    }

    Tournament owned(UserPrincipal me, long id) {
        Tournament t = loader.tournament(id);
        if (!t.isOwnedBy(me.playerId())) {
            throw ApiException.forbidden("NOT_TOURNAMENT_ORGANIZER", "Solo el organizador del torneo puede hacer esto");
        }
        return t;
    }

    private static void requireOpen(Tournament t) {
        if (t.getStatus() != Status.OPEN) {
            throw ApiException.conflict("REGISTRATION_CLOSED", "El torneo ya comenzó: no se pueden cambiar las inscripciones");
        }
    }
}
