package cl.chessquery.users.rating;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Un punto de la serie de rating de un jugador en una modalidad. */
@Entity
@Table(name = "rating_history")
@Getter
@NoArgsConstructor
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_type", nullable = false, length = 30)
    private RatingType ratingType;

    @Column(name = "rating_value", nullable = false)
    private Integer ratingValue;

    /** Valor anterior y delta desnormalizados: el gráfico no necesita self-join. */
    @Column(name = "rating_prev_value")
    private Integer ratingPrevValue;

    private Short delta;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(length = 50)
    private String source;

    public RatingHistory(Long playerId, RatingType type, int value, Integer previous, Instant recordedAt, String source) {
        this.playerId = playerId;
        this.ratingType = type;
        this.ratingValue = value;
        this.ratingPrevValue = previous;
        this.delta = previous == null ? null : (short) (value - previous);
        this.recordedAt = recordedAt;
        this.source = source;
    }

    public record Point(Instant recordedAt, int rating, Integer previous, Short delta, String source) {
        public static Point of(RatingHistory h) {
            return new Point(h.recordedAt, h.ratingValue, h.ratingPrevValue, h.delta, h.source);
        }
    }
}
