package cl.chessquery.users.friends;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Amistad entre dos jugadores. Una sola fila por par: quien pidió es {@code requesterId} y
 * quien recibió {@code addresseeId}; al aceptar la relación es simétrica. Rechazar o dejar de
 * ser amigos borra la fila (no hay estados DECLINED/REMOVED).
 */
@Entity
@Table(name = "friendship")
@Getter @Setter
@NoArgsConstructor
public class Friendship {

    public enum Status { PENDING, ACCEPTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "addressee_id", nullable = false)
    private Long addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public Friendship(Long requesterId, Long addresseeId) {
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    /** El otro extremo visto desde {@code playerId}. */
    public Long otherSide(Long playerId) {
        return requesterId.equals(playerId) ? addresseeId : requesterId;
    }

    public void accept() {
        status = Status.ACCEPTED;
        respondedAt = Instant.now();
    }
}
