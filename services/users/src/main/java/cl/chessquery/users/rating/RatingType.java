package cl.chessquery.users.rating;

import cl.chessquery.users.player.Player;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Modalidades de rating que la plataforma conoce. Cada una sabe leer y escribir su columna snapshot en
 * {@code player}; su serie vive en {@code rating_history}. Para sumar una modalidad basta una línea acá
 * (más su columna en una migración): no hay switches repartidos que mantener sincronizados.
 */
public enum RatingType {
    NATIONAL(Player::getEloNational, Player::setEloNational),
    FIDE_STANDARD(Player::getEloFideStandard, Player::setEloFideStandard),
    FIDE_RAPID(Player::getEloFideRapid, Player::setEloFideRapid),
    FIDE_BLITZ(Player::getEloFideBlitz, Player::setEloFideBlitz),
    // ELO ChessQuery, uno por ritmo (TimeControlCategory): lo actualizan game y tournament con elo.updated
    PLATFORM_BULLET(Player::getEloPlatformBullet, Player::setEloPlatformBullet),
    PLATFORM_BLITZ(Player::getEloPlatformBlitz, Player::setEloPlatformBlitz),
    PLATFORM_RAPID(Player::getEloPlatformRapid, Player::setEloPlatformRapid),
    PLATFORM_CLASSICAL(Player::getEloPlatformClassical, Player::setEloPlatformClassical),
    LICHESS_BULLET(Player::getEloLichessBullet, Player::setEloLichessBullet),
    LICHESS_BLITZ(Player::getEloLichessBlitz, Player::setEloLichessBlitz),
    LICHESS_RAPID(Player::getEloLichessRapid, Player::setEloLichessRapid),
    LICHESS_CLASSICAL(Player::getEloLichessClassical, Player::setEloLichessClassical),
    CHESSCOM_BULLET(Player::getEloChesscomBullet, Player::setEloChesscomBullet),
    CHESSCOM_BLITZ(Player::getEloChesscomBlitz, Player::setEloChesscomBlitz),
    CHESSCOM_RAPID(Player::getEloChesscomRapid, Player::setEloChesscomRapid),
    CHESSCOM_DAILY(Player::getEloChesscomDaily, Player::setEloChesscomDaily);

    private final Function<Player, Integer> getter;
    private final BiConsumer<Player, Integer> setter;

    RatingType(Function<Player, Integer> getter, BiConsumer<Player, Integer> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    public Integer read(Player player) {
        return getter.apply(player);
    }

    public void write(Player player, Integer value) {
        setter.accept(player, value);
    }
}
