package cl.chessquery.game.room;

import cl.chessquery.common.rating.TimeControlCategory;
import cl.chessquery.game.api.GameDtos.GameView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Contratos JSON de las salas de juego (espejo en apps/web/src/api/roomTypes.ts). */
public final class RoomDtos {

    private RoomDtos() {}

    /** Crear o reconfigurar. {@code maxPlayers} null = 2 por tablero; puede ser mayor (el resto mira). */
    public record RoomRequest(@NotBlank @Size(max = 120) String name,
                              @Min(1) @Max(16) int boards,
                              @Min(2) @Max(64) Integer maxPlayers,
                              @Min(1) @Max(180) int minutes,
                              @Min(0) @Max(180) int incrementSeconds) {

        int playersOrDefault() {
            return maxPlayers != null ? maxPlayers : Math.min(64, Math.max(2, boards * 2));
        }
    }

    public record JoinRequest(@NotBlank @Size(max = 20) String code) {}

    /** Quién juega en un tablero; null en un color = puesto libre. */
    public record AssignRequest(Long whitePlayerId, Long blackPlayerId) {}

    public record Seat(Long playerId, String name) {}

    /** Un miembro y su puesto: {@code boardNo} null = espectador. */
    public record MemberView(Long playerId, String name, Integer boardNo, String color) {}

    public record BoardView(int boardNo, Seat white, Seat black, GameView game) {}

    /**
     * La sala tal como la ve quien la consulta: {@code organizer} si es el dueño; {@code myBoard}/{@code myGameId}
     * para llevar al jugador a su partida en cuanto empieza.
     */
    public record RoomView(Long id, String name, String code, RoomStatus status, int boardCount, int maxPlayers,
                           int initialSeconds, int incrementSeconds, TimeControlCategory category, long version,
                           boolean organizer, Integer myBoard, Long myGameId,
                           List<MemberView> members, List<BoardView> boards) {}

    public record RoomSummary(Long id, String name, String code, RoomStatus status, int boardCount, int maxPlayers,
                              long memberCount, TimeControlCategory category, Instant createdAt) {}

    /** Salas que organizo y salas en las que entré. */
    public record Mine(List<RoomSummary> organized, List<RoomSummary> joined) {}
}
