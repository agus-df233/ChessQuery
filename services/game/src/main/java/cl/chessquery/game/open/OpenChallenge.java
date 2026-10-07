package cl.chessquery.game.open;

import cl.chessquery.game.api.GameDtos.ColorChoice;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Desafío abierto: lo acepta el primero que entre con el enlace. {@code version} evita que lo tomen dos. */
@Entity
@Table(name = "open_challenge")
@Getter
@Setter
@NoArgsConstructor
public class OpenChallenge {

    public enum Status { OPEN, ACCEPTED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;
    private Long challengerId;
    private String challengerName;
    private int minutes;
    private int incrementSeconds;

    @Enumerated(EnumType.STRING)
    private ColorChoice color;

    private boolean rated;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    private Long gameId;
    private Instant createdAt;

    @Version
    private long version;
}
