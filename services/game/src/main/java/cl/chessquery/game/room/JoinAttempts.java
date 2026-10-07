package cl.chessquery.game.room;

import cl.chessquery.common.api.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos para entrar a salas: 10 por minuto por jugador. Con los códigos de {@link RoomCodes} eso hace
 * inútil probar códigos al azar. En memoria (game corre con una réplica; con más, cada una tiene su propio límite,
 * que igual sigue siendo bajo).
 */
@Component
@RequiredArgsConstructor
public class JoinAttempts {

    static final int MAX_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<Long, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public void register(long playerId) {
        Instant now = clock.instant();
        Deque<Instant> recent = attempts.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(WINDOW))) recent.pollFirst();
            if (recent.size() >= MAX_PER_WINDOW) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                        "Demasiados intentos; espera un minuto y vuelve a ingresar el código");
            }
            recent.addLast(now);
        }
    }
}
