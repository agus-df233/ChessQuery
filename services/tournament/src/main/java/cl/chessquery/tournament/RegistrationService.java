package cl.chessquery.tournament;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.tournament.api.TournamentDtos.BulkRow;
import cl.chessquery.tournament.api.TournamentDtos.CheckinResult;
import cl.chessquery.tournament.api.TournamentDtos.Detail;
import cl.chessquery.tournament.api.TournamentDtos.RegistrationView;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.RegistrationStatus;
import cl.chessquery.tournament.domain.Repositories;
import cl.chessquery.tournament.domain.Status;
import cl.chessquery.tournament.domain.Tournament;
import cl.chessquery.tournament.users.UsersClient;
import cl.chessquery.tournament.users.UsersClient.PlayerSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inscripciones de un torneo, de punta a punta:
 * <ul>
 *   <li><b>El jugador se inscribe</b> respetando las reglas del torneo: fecha de cierre, rango de rating, cupo (si está
 *       lleno queda en lista de espera) y, si el organizador lo pide, su aprobación.</li>
 *   <li><b>El organizador</b> inscribe directo (de a uno o en bloque, desde su roster), aprueba, rechaza y retira.
 *       Sus inscripciones son confirmadas aunque el cupo esté lleno: él decide.</li>
 *   <li>Cuando se libera un cupo, <b>avanza la lista de espera</b> por orden de llegada.</li>
 *   <li><b>Acreditación</b> el día del torneo con el QR de cada jugador (o a mano); si el torneo la exige, quien no se
 *       acreditó no juega la ronda 1 ({@link #markNoShows}).</li>
 *   <li><b>Retiro</b> durante el torneo: deja de emparejarse desde la ronda siguiente; sus resultados quedan.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final Repositories.Registrations registrations;
    private final Repositories.Rounds rounds;
    private final TournamentService lifecycle;
    private final TournamentLoader loader;
    private final TournamentQueries queries;
    private final UsersClient users;
    private final Clock clock;

    /** El jugador se inscribe a sí mismo con las reglas del torneo. */
    @Transactional
    public Detail join(UserPrincipal me, long id) {
        Tournament t = loader.tournament(id);
        requireOpen(t);
        if (t.getRegistrationClosesAt() != null && clock.instant().isAfter(t.getRegistrationClosesAt())) {
            throw ApiException.conflict("REGISTRATION_DEADLINE_PASSED", "La inscripción de este torneo ya cerró");
        }
        PlayerSummary p = users.player(me.playerId());
        int rating = p.seedRating(t.category());
        if ((t.getMinRating() != null && rating < t.getMinRating()) || (t.getMaxRating() != null && rating > t.getMaxRating())) {
            throw ApiException.conflict("RATING_OUT_OF_RANGE", "Tu rating (" + rating + ") está fuera del rango del torneo");
        }
        RegistrationStatus status = full(t) ? RegistrationStatus.WAITLIST
                : t.isRequiresApproval() ? RegistrationStatus.PENDING : RegistrationStatus.CONFIRMED;
        enroll(t, p, status);
        return queries.detail(id);
    }

    /** El organizador inscribe a un jugador (de su roster o registrado): queda confirmado. */
    @Transactional
    public Detail register(UserPrincipal me, long id, long playerId) {
        Tournament t = lifecycle.owned(me, id);
        requireOpen(t);
        enroll(t, users.player(playerId), RegistrationStatus.CONFIRMED);
        return queries.detail(id);
    }

    /** Inscripción en bloque (p. ej. todo el roster recién cargado): informe por jugador, sin cortar en el primer error. */
    @Transactional
    public List<BulkRow> registerAll(UserPrincipal me, long id, List<Long> playerIds) {
        Tournament t = lifecycle.owned(me, id);
        requireOpen(t);
        Map<Long, PlayerSummary> found = users.players(playerIds).stream()
                .collect(Collectors.toMap(PlayerSummary::id, Function.identity(), (a, b) -> a));
        List<BulkRow> report = new ArrayList<>();
        for (Long playerId : playerIds.stream().distinct().toList()) {
            report.add(bulkRow(t, playerId, found.get(playerId)));
        }
        return report;
    }

    private BulkRow bulkRow(Tournament t, Long playerId, PlayerSummary p) {
        if (p == null) return new BulkRow(playerId, "ERROR", "Jugador no encontrado");
        if (registrations.findByTournamentIdAndPlayerId(t.getId(), playerId).isPresent()) {
            return new BulkRow(playerId, "ALREADY_REGISTERED", null);
        }
        enroll(t, p, RegistrationStatus.CONFIRMED);
        return new BulkRow(playerId, "REGISTERED", null);
    }

    /** Aprueba una inscripción pendiente (o sube a alguien de la lista de espera si hay cupo). */
    @Transactional
    public Detail approve(UserPrincipal me, long id, long playerId) {
        Tournament t = lifecycle.owned(me, id);
        requireOpen(t);
        Registration r = require(id, playerId);
        if (r.getStatus() == RegistrationStatus.WAITLIST && full(t)) {
            throw ApiException.conflict("TOURNAMENT_FULL", "El torneo está completo: libera un cupo primero");
        }
        if (r.getStatus() != RegistrationStatus.PENDING && r.getStatus() != RegistrationStatus.WAITLIST) {
            throw ApiException.conflict("NOT_PENDING", "Esa inscripción no está pendiente");
        }
        r.setStatus(RegistrationStatus.CONFIRMED);
        return queries.detail(id);
    }

    /** Antes de empezar: el organizador rechaza o el jugador se retira; el cupo pasa al primero de la espera. */
    @Transactional
    public Detail unregister(UserPrincipal me, long id, long playerId) {
        Tournament t = loader.tournament(id);
        if (!t.isOwnedBy(me.playerId()) && me.playerId() != playerId) {
            throw ApiException.forbidden("NOT_ALLOWED", "Solo el organizador o el propio jugador pueden retirar la inscripción");
        }
        requireOpen(t);
        registrations.findByTournamentIdAndPlayerId(id, playerId).ifPresent(r -> {
            boolean freedSeat = r.holdsSeat();
            registrations.delete(r);
            registrations.flush();
            if (freedSeat) promoteWaitlist(t);
        });
        return queries.detail(id);
    }

    /** Durante el torneo: el jugador deja de emparejarse desde la ronda siguiente (sus resultados quedan). */
    @Transactional
    public Detail withdraw(UserPrincipal me, long id, long playerId) {
        Tournament t = lifecycle.owned(me, id);
        if (t.getStatus() != Status.IN_PROGRESS) {
            throw ApiException.conflict("NOT_IN_PROGRESS", "Antes de empezar, quita la inscripción en vez de retirar");
        }
        Registration r = require(id, playerId);
        if (r.getStatus() != RegistrationStatus.CONFIRMED) {
            throw ApiException.conflict("NOT_PLAYING", "Ese jugador no está jugando el torneo");
        }
        r.setStatus(RegistrationStatus.WITHDRAWN);
        r.setWithdrawnFromRound(rounds.findByTournamentIdOrderByNumberAsc(id).size() + 1);
        log.info("Torneo {}: jugador {} retirado desde la ronda {}", id, playerId, r.getWithdrawnFromRound());
        return queries.detail(id);
    }

    /** Acreditación con el QR del jugador (el código de su inscripción). Repetir no falla: avisa que ya estaba. */
    @Transactional
    public CheckinResult checkinByCode(UserPrincipal me, long id, String code) {
        Tournament t = lifecycle.owned(me, id);
        requireOpen(t);
        Registration r = registrations.findByCheckinCode(code == null ? "" : code.trim())
                .filter(x -> x.getTournamentId() == id)
                .orElseThrow(() -> ApiException.notFound("CODE_NOT_FOUND", "Ese código no es de una inscripción de este torneo"));
        return checkin(r);
    }

    /** Acreditación manual (jugador sin celular, QR dañado): lo marca el organizador desde la lista. */
    @Transactional
    public CheckinResult checkinManually(UserPrincipal me, long id, long playerId) {
        requireOpen(lifecycle.owned(me, id));
        return checkin(require(id, playerId));
    }

    @Transactional
    public RegistrationView undoCheckin(UserPrincipal me, long id, long playerId) {
        requireOpen(lifecycle.owned(me, id));
        Registration r = require(id, playerId);
        r.setCheckedInAt(null);
        return RegistrationView.of(r);
    }

    /** Todas las inscripciones, con su estado y código (para la acreditación y las credenciales impresas). */
    @Transactional(readOnly = true)
    public List<RegistrationView> list(UserPrincipal me, long id) {
        lifecycle.owned(me, id);
        return registrations.findByTournamentIdOrderBySeedRatingDescIdAsc(id).stream()
                .sorted(Comparator.comparing(Registration::getStatus))
                .map(RegistrationView::of).toList();
    }

    /** Mi inscripción (estado y QR de acreditación). */
    @Transactional(readOnly = true)
    public RegistrationView mine(UserPrincipal me, long id) {
        return RegistrationView.of(registrations.findByTournamentIdAndPlayerId(id, me.playerId())
                .orElseThrow(() -> ApiException.notFound("NOT_REGISTERED", "No estás inscrito en este torneo")));
    }

    /**
     * Al generar la ronda 1 de un torneo que exige acreditación: los confirmados que no se acreditaron quedan como
     * "no se presentó" (retirados desde la ronda 1) y no se emparejan. Devuelve cuántos.
     */
    int markNoShows(Tournament t) {
        if (!t.isCheckinRequired() || t.getStatus() != Status.OPEN) return 0;
        List<Registration> absent = registrations.findByTournamentIdOrderBySeedRatingDescIdAsc(t.getId()).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED && r.getCheckedInAt() == null).toList();
        absent.forEach(r -> {
            r.setStatus(RegistrationStatus.WITHDRAWN);
            r.setWithdrawnFromRound(1);
        });
        registrations.flush();
        return absent.size();
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private CheckinResult checkin(Registration r) {
        if (r.getStatus() != RegistrationStatus.CONFIRMED) {
            throw ApiException.conflict("NOT_CONFIRMED", "Esa inscripción no está confirmada");
        }
        boolean already = r.getCheckedInAt() != null;
        if (!already) r.setCheckedInAt(clock.instant());
        return new CheckinResult(RegistrationView.of(r), already);
    }

    private void enroll(Tournament t, PlayerSummary p, RegistrationStatus status) {
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
        r.setStatus(status);
        r.setCreatedAt(clock.instant());
        registrations.save(r);
    }

    /** El primero de la lista de espera toma el cupo (pendiente de aprobación si el torneo la exige). */
    private void promoteWaitlist(Tournament t) {
        if (full(t)) return;
        registrations.findByTournamentIdOrderBySeedRatingDescIdAsc(t.getId()).stream()
                .filter(r -> r.getStatus() == RegistrationStatus.WAITLIST)
                .min(Comparator.comparing(Registration::getCreatedAt).thenComparing(Registration::getId))
                .ifPresent(r -> r.setStatus(t.isRequiresApproval() ? RegistrationStatus.PENDING : RegistrationStatus.CONFIRMED));
    }

    private boolean full(Tournament t) {
        if (t.getMaxPlayers() == null) return false;
        long taken = registrations.findByTournamentIdOrderBySeedRatingDescIdAsc(t.getId()).stream()
                .filter(Registration::holdsSeat).count();
        return taken >= t.getMaxPlayers();
    }

    private Registration require(long id, long playerId) {
        return registrations.findByTournamentIdAndPlayerId(id, playerId)
                .orElseThrow(() -> ApiException.notFound("NOT_REGISTERED", "Ese jugador no está inscrito"));
    }

    private static void requireOpen(Tournament t) {
        if (t.getStatus() != Status.OPEN) {
            throw ApiException.conflict("REGISTRATION_CLOSED", "El torneo ya comenzó: no se pueden cambiar las inscripciones");
        }
    }
}
