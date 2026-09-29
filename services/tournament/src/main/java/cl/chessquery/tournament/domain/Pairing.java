package cl.chessquery.tournament.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una mesa de una ronda. {@code blackPlayerId == null} es bye (el resultado queda BYE al generarla). */
@Entity
@Table(name = "pairing")
@Getter
@Setter
@NoArgsConstructor
public class Pairing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roundId;
    private int board;
    private Long whitePlayerId;
    private Long blackPlayerId;

    @Enumerated(EnumType.STRING)
    private Result result;

    public boolean involves(long playerId) {
        return whitePlayerId == playerId || (blackPlayerId != null && blackPlayerId == playerId);
    }
}
