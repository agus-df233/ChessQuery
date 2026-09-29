package cl.chessquery.users.rating;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Cómo aplica una fuente externa ({@code source} de {@code rating.updated}) los datos de un jugador.
 * Cada fuente declara qué nombres atiende; {@link RatingUpdatedConsumer} solo reparte. Para sumar una
 * fuente nueva se agrega una implementación, sin tocar el consumer (ver docs/etl/nueva-fuente.md).
 */
public interface RatingSource {

    /** Valores de {@code source} que atiende (en mayúsculas), p. ej. {@code FIDE}, {@code LICHESS}. */
    Set<String> sources();

    /** Aplica un jugador del lote. Devuelve true si tocó a alguien. */
    boolean apply(Map<String, Object> player, String source, Instant recordedAt);
}
