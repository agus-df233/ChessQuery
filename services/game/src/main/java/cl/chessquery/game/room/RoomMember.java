package cl.chessquery.game.room;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/** Jugador que entró a la sala con el código. Si no tiene tablero asignado, mira como espectador. */
@Entity
@Table(name = "room_member")
@IdClass(RoomMember.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class RoomMember {

    public record Key(Long roomId, Long playerId) implements Serializable {
        public Key() { this(null, null); }
    }

    @Id
    private Long roomId;
    @Id
    private Long playerId;

    private String publicName;
    private Instant joinedAt;

    public RoomMember(long roomId, long playerId, String publicName, Instant joinedAt) {
        this.roomId = roomId;
        this.playerId = playerId;
        this.publicName = publicName;
        this.joinedAt = joinedAt;
    }
}
