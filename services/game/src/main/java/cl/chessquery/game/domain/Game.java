package cl.chessquery.game.domain;

import cl.chessquery.common.rating.TimeControlCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Partida. {@code whiteMs}/{@code blackMs} son el tiempo restante al comenzar el turno en curso
 * ({@code turnStartedAt}); el tiempo real del que juega se calcula restando lo transcurrido (ver {@link #remainingMs}).
 * {@code version} sube en cada cambio: el long polling la usa para saber si hay novedades.
 */
@Entity
@Table(name = "game")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long whitePlayerId;
    private Long blackPlayerId;
    private Long challengerId;
    private String whiteName;
    private String blackName;

    @Enumerated(EnumType.STRING)
    private GameStatus status = GameStatus.PENDING;

    private int initialSeconds;
    private int incrementSeconds;
    private boolean rated;
    private String movesUci = "";
    private String movesSan = "";
    private String fen;
    private long whiteMs;
    private long blackMs;
    private Instant turnStartedAt;
    private Long drawOfferBy;

    @Enumerated(EnumType.STRING)
    private Outcome result;

    @Enumerated(EnumType.STRING)
    private Termination termination;

    private Integer whiteRatingBefore;
    private Integer blackRatingBefore;
    private boolean whiteUnrated;
    private boolean blackUnrated;
    private Integer whiteRatingAfter;
    private Integer blackRatingAfter;
    private String pgn;

    @Version
    private long version;

    private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant finishedAt;

    /** Ritmo de la partida: define qué ELO ChessQuery se usa y se actualiza. */
    public TimeControlCategory category() {
        return TimeControlCategory.of(initialSeconds, incrementSeconds);
    }

    public static List<String> split(String moves) {
        return moves == null || moves.isBlank() ? List.of() : Arrays.asList(moves.trim().split(" "));
    }

    public List<String> uciMoves() { return split(movesUci); }

    public List<String> sanMoves() { return split(movesSan); }

    public int ply() { return uciMoves().size(); }

    public boolean whiteToMove() { return ply() % 2 == 0; }

    public boolean plays(long playerId) {
        return whitePlayerId == playerId || blackPlayerId == playerId;
    }

    public boolean isWhite(long playerId) { return whitePlayerId == playerId; }

    public long opponentOf(long playerId) { return isWhite(playerId) ? blackPlayerId : whitePlayerId; }

    /** Tiempo que le queda a un color ahora mismo (solo corre el del turno, y solo en partida activa). */
    public long remainingMs(boolean white, Instant now) {
        long stored = white ? whiteMs : blackMs;
        if (status != GameStatus.ACTIVE || turnStartedAt == null || white != whiteToMove()) return stored;
        return Math.max(0, stored - (now.toEpochMilli() - turnStartedAt.toEpochMilli()));
    }
}
