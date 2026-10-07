package cl.chessquery.game.room;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Un tablero de la sala: quién juega con cada color y su última partida (en juego o terminada). */
@Entity
@Table(name = "room_board")
@IdClass(RoomBoard.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class RoomBoard {

    public record Key(Long roomId, Integer boardNo) implements Serializable {
        public Key() { this(null, null); }
    }

    @Id
    private Long roomId;
    @Id
    private Integer boardNo;

    private Long whitePlayerId;
    private Long blackPlayerId;
    private Long gameId;

    public RoomBoard(long roomId, int boardNo) {
        this.roomId = roomId;
        this.boardNo = boardNo;
    }

    public boolean seats(long playerId) {
        return Long.valueOf(playerId).equals(whitePlayerId) || Long.valueOf(playerId).equals(blackPlayerId);
    }

    public boolean ready() {
        return whitePlayerId != null && blackPlayerId != null;
    }

    public void clearSeat(long playerId) {
        if (Long.valueOf(playerId).equals(whitePlayerId)) whitePlayerId = null;
        if (Long.valueOf(playerId).equals(blackPlayerId)) blackPlayerId = null;
    }
}
