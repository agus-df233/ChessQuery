package cl.chessquery.tournament.api;

import cl.chessquery.common.rating.TimeControlCategory;
import cl.chessquery.tournament.domain.Format;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.RegistrationStatus;
import cl.chessquery.tournament.domain.Result;
import cl.chessquery.tournament.domain.Status;
import cl.chessquery.tournament.domain.Tournament;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos JSON de la API de torneos (espejo en apps/web/src/api/tournamentTypes.ts). */
public final class TournamentDtos {

    private TournamentDtos() {}

    public record UpsertRequest(@NotBlank @Size(max = 160) String name, @Size(max = 100) String city,
                                @Size(max = 100) String region, @NotNull LocalDate startDate, LocalDate endDate,
                                @NotNull Format format, @Min(1) @Max(30) int rounds,
                                @Size(max = 40) String timeControl, Boolean rated,
                                @Min(1) @Max(300) Integer baseMinutes, @Min(0) @Max(180) Integer incrementSeconds,
                                Instant registrationClosesAt, @Min(2) @Max(500) Integer maxPlayers,
                                Boolean requiresApproval, @Min(0) @Max(3500) Integer minRating,
                                @Min(0) @Max(3500) Integer maxRating, Boolean checkinRequired) {}

    /** Inscripción en bloque (p. ej. el roster recién cargado). */
    public record BulkRegisterRequest(@NotEmpty @Size(max = 500) List<@NotNull Long> playerIds) {}

    /** Resultado por jugador: REGISTERED, ALREADY_REGISTERED o ERROR (con motivo). */
    public record BulkRow(Long playerId, String outcome, String message) {}

    public record CheckinRequest(@NotBlank @Size(max = 64) String code) {}

    public record RegisterRequest(@NotNull Long playerId) {}

    public record ResultRequest(@NotNull Result result) {}

    /**
     * {@code playerCount}: los que juegan (confirmados); {@code pendingCount}/{@code waitlistCount}: esperando
     * aprobación o cupo. Las reglas de inscripción en null = sin límite.
     */
    public record TournamentView(Long id, String name, String city, String region, LocalDate startDate,
                                 LocalDate endDate, Format format, int roundsPlanned, int currentRound,
                                 String timeControl, Integer baseMinutes, Integer incrementSeconds,
                                 TimeControlCategory category, boolean rated, Status status, Long organizationId,
                                 int playerCount, int pendingCount, int waitlistCount,
                                 Instant registrationClosesAt, Integer maxPlayers, boolean requiresApproval,
                                 Integer minRating, Integer maxRating, boolean checkinRequired) {

        public static TournamentView of(Tournament t, int currentRound, List<Registration> all) {
            return new TournamentView(t.getId(), t.getName(), t.getCity(), t.getRegion(), t.getStartDate(),
                    t.getEndDate(), t.getFormat(), t.getRoundsPlanned(), currentRound, t.getTimeControl(),
                    t.getBaseMinutes(), t.getIncrementSeconds(), t.category(), t.isRated(),
                    t.getStatus(), t.getOrganizationId(),
                    count(all, RegistrationStatus.CONFIRMED), count(all, RegistrationStatus.PENDING),
                    count(all, RegistrationStatus.WAITLIST), t.getRegistrationClosesAt(), t.getMaxPlayers(),
                    t.isRequiresApproval(), t.getMinRating(), t.getMaxRating(), t.isCheckinRequired());
        }

        private static int count(List<Registration> all, RegistrationStatus status) {
            return (int) all.stream().filter(r -> r.getStatus() == status).count();
        }
    }

    /** Una inscripción vista por el organizador o por el propio jugador (con el código de su QR de acreditación). */
    public record RegistrationView(Long playerId, String name, String title, String clubName, int seedRating,
                                   RegistrationStatus status, Instant checkedInAt, Integer withdrawnFromRound,
                                   String checkinCode, Instant createdAt) {

        public static RegistrationView of(Registration r) {
            return new RegistrationView(r.getPlayerId(), r.getFirstName() + " " + r.getLastName(), r.getTitle(),
                    r.getClubName(), r.getSeedRating(), r.getStatus(), r.getCheckedInAt(), r.getWithdrawnFromRound(),
                    r.getCheckinCode(), r.getCreatedAt());
        }
    }

    /** {@code alreadyCheckedIn}: el QR ya se había leído (no es error: se avisa en la pantalla de acreditación). */
    public record CheckinResult(RegistrationView registration, boolean alreadyCheckedIn) {}

    /** Jugador tal como se muestra a terceros (apellido de menores abreviado desde users). */
    public record PlayerRef(Long playerId, String name, String title, Integer rating) {

        public static PlayerRef of(Registration r) {
            return r == null ? null : new PlayerRef(r.getPlayerId(), r.getFirstName() + " " + r.getLastName(),
                    r.getTitle(), r.getSeedRating());
        }
    }

    /** {@code withdrawnFromRound}: null = juega; N = retirado desde la ronda N. */
    public record EntryView(int startRank, PlayerRef player, String clubName, Integer withdrawnFromRound) {}

    public record Detail(TournamentView tournament, List<EntryView> players) {}

    public record BoardView(int board, PlayerRef white, PlayerRef black, Result result, String resultLabel) {}

    public record RoundView(int number, boolean complete, List<BoardView> boards) {}

    public record StandingView(int position, PlayerRef player, double points, double buchholzCut1, double buchholz,
                               double sonnebornBerger, int wins, int played) {}

    public record Mine(List<TournamentView> organized, List<TournamentView> registered) {}
}
