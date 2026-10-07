package cl.chessquery.game.room;

import cl.chessquery.common.rating.TimeControlCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Sala de juego de un organizador (colegio o club): N tableros para una clase o una práctica. Los jugadores entran
 * con {@code code}; {@code version} sube con cada cambio de la sala o de sus partidas (lo usa el tiempo real).
 */
@Entity
@Table(name = "room")
@Getter
@Setter
@NoArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizationId;
    private Long organizerId;
    private String name;
    private String code;
    private int boards;
    private int maxPlayers;
    private int initialSeconds;
    private int incrementSeconds;

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.OPEN;

    private long version;
    private Instant createdAt;
    private Instant closedAt;

    public boolean isOwnedBy(long playerId) {
        return organizerId != null && organizerId == playerId;
    }

    public boolean isOpen() {
        return status == RoomStatus.OPEN;
    }

    public TimeControlCategory category() {
        return TimeControlCategory.of(initialSeconds, incrementSeconds);
    }

    /** Algo cambió: la vista en vivo (WebSocket y long polling) se entera por la versión. */
    public void touch() {
        version++;
    }
}
