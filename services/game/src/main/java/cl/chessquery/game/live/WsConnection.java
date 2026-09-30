package cl.chessquery.game.live;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Una conexión en vivo: quién la abrió y qué partida sigue (null hasta que se suscribe). */
@Entity
@Table(name = "ws_connection")
@Getter
@Setter
@NoArgsConstructor
public class WsConnection {

    @Id
    private String connectionId;

    private Long playerId;
    private Long gameId;
    private Instant connectedAt;
    private Instant lastSeenAt;

    public WsConnection(String connectionId, long playerId, Instant now) {
        this.connectionId = connectionId;
        this.playerId = playerId;
        this.connectedAt = now;
        this.lastSeenAt = now;
    }
}
