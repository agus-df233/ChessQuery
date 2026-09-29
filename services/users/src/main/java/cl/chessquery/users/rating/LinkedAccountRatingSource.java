package cl.chessquery.users.rating;

import cl.chessquery.users.events.Payloads;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plataformas online (Lichess, Chess.com): solo enriquece a quien vinculó ese username. Nunca crea
 * jugadores, porque un username no es una identidad.
 */
@Component
@RequiredArgsConstructor
public class LinkedAccountRatingSource implements RatingSource {

    private static final Map<RatingType, String> LICHESS = Map.of(
            RatingType.LICHESS_BULLET, "eloLichessBullet", RatingType.LICHESS_BLITZ, "eloLichessBlitz",
            RatingType.LICHESS_RAPID, "eloLichessRapid", RatingType.LICHESS_CLASSICAL, "eloLichessClassical");
    private static final Map<RatingType, String> CHESSCOM = Map.of(
            RatingType.CHESSCOM_BULLET, "eloChesscomBullet", RatingType.CHESSCOM_BLITZ, "eloChesscomBlitz",
            RatingType.CHESSCOM_RAPID, "eloChesscomRapid", RatingType.CHESSCOM_DAILY, "eloChesscomDaily");

    private final PlayerRepository players;
    private final RatingService ratings;

    @Override
    public Set<String> sources() {
        return Set.of("LICHESS", "CHESSCOM");
    }

    @Override
    public boolean apply(Map<String, Object> p, String source, Instant at) {
        boolean lichess = "LICHESS".equals(source);
        Optional<Player> match = lichess
                ? players.findByLichessUsernameIgnoreCase(Payloads.str(p, "lichessUsername"))
                : players.findByChesscomUsernameIgnoreCase(Payloads.str(p, "chesscomUsername"));
        if (match.isEmpty()) return false;
        Player player = match.get();
        boolean changed = false;
        for (var field : (lichess ? LICHESS : CHESSCOM).entrySet()) {
            changed |= ratings.apply(player, field.getKey(), Payloads.integer(p, field.getValue()), at, source);
        }
        if (changed) ratings.markEnriched(player, source);
        return changed;
    }
}
