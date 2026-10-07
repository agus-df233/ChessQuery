package cl.chessquery.tournament.api;

import cl.chessquery.common.rating.TimeControlCategory;
import cl.chessquery.tournament.domain.Format;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.Result;
import cl.chessquery.tournament.domain.Status;
import cl.chessquery.tournament.domain.Tournament;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Contratos JSON de la API de torneos (espejo en apps/web/src/api/tournamentTypes.ts). */
public final class TournamentDtos {

    private TournamentDtos() {}

    public record UpsertRequest(@NotBlank @Size(max = 160) String name, @Size(max = 100) String city,
                                @Size(max = 100) String region, @NotNull LocalDate startDate, LocalDate endDate,
                                @NotNull Format format, @Min(1) @Max(30) int rounds,
                                @Size(max = 40) String timeControl, Boolean rated,
                                @Min(1) @Max(300) Integer baseMinutes, @Min(0) @Max(180) Integer incrementSeconds) {}

    public record RegisterRequest(@NotNull Long playerId) {}

    public record ResultRequest(@NotNull Result result) {}

    public record TournamentView(Long id, String name, String city, String region, LocalDate startDate,
                                 LocalDate endDate, Format format, int roundsPlanned, int currentRound,
                                 String timeControl, Integer baseMinutes, Integer incrementSeconds,
                                 TimeControlCategory category, boolean rated, Status status, Long organizationId,
                                 int playerCount) {

        public static TournamentView of(Tournament t, int currentRound, int playerCount) {
            return new TournamentView(t.getId(), t.getName(), t.getCity(), t.getRegion(), t.getStartDate(),
                    t.getEndDate(), t.getFormat(), t.getRoundsPlanned(), currentRound, t.getTimeControl(),
                    t.getBaseMinutes(), t.getIncrementSeconds(), t.category(), t.isRated(),
                    t.getStatus(), t.getOrganizationId(), playerCount);
        }
    }

    /** Jugador tal como se muestra a terceros (apellido de menores abreviado desde users). */
    public record PlayerRef(Long playerId, String name, String title, Integer rating) {

        public static PlayerRef of(Registration r) {
            return r == null ? null : new PlayerRef(r.getPlayerId(), r.getFirstName() + " " + r.getLastName(),
                    r.getTitle(), r.getSeedRating());
        }
    }

    public record EntryView(int startRank, PlayerRef player, String clubName) {}

    public record Detail(TournamentView tournament, List<EntryView> players) {}

    public record BoardView(int board, PlayerRef white, PlayerRef black, Result result, String resultLabel) {}

    public record RoundView(int number, boolean complete, List<BoardView> boards) {}

    public record StandingView(int position, PlayerRef player, double points, double buchholzCut1, double buchholz,
                               double sonnebornBerger, int wins, int played) {}

    public record Mine(List<TournamentView> organized, List<TournamentView> registered) {}
}
