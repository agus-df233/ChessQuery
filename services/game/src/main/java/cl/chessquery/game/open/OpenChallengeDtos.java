package cl.chessquery.game.open;

import cl.chessquery.common.rating.TimeControlCategory;
import cl.chessquery.game.api.GameDtos.ColorChoice;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

/** Contratos del desafío abierto (espejo en apps/web/src/api/gameTypes.ts). */
public final class OpenChallengeDtos {

    private OpenChallengeDtos() {}

    public record OpenChallengeRequest(@Min(1) @Max(180) int minutes, @Min(0) @Max(180) int incrementSeconds,
                                       ColorChoice color, Boolean rated) {}

    /** {@code status}: OPEN, ACCEPTED (con {@code gameId}), CANCELLED o EXPIRED (vencido sin respuesta). */
    public record OpenChallengeView(String token, Long challengerId, String challengerName, int minutes,
                                    int incrementSeconds, TimeControlCategory category, ColorChoice color,
                                    boolean rated, String status, Long gameId, Instant expiresAt, boolean mine) {}
}
