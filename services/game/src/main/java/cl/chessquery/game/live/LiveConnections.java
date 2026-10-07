package cl.chessquery.game.live;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.game.GameQueries;
import cl.chessquery.game.api.GameDtos.GameView;
import cl.chessquery.game.room.RoomChanged;
import cl.chessquery.game.room.RoomDtos.RoomView;
import cl.chessquery.game.room.RoomQueries;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Partidas en vivo por WebSocket. El mismo protocolo en local ({@code /ws}) y en la nube (API Gateway WebSocket):
 * <ol>
 *   <li><b>Conectar</b> con {@code ?token=<access token>}: se valida igual que un Bearer (mismo {@link JwtDecoder}).</li>
 *   <li><b>Suscribirse</b> con {@code {"action":"subscribe","gameId":N}}: se responde con el estado actual. O a una
 *       sala de juego completa con {@code {"action":"subscribe","roomId":N}} (solo su organizador y sus miembros):
 *       llega {@code {"type":"room","room":<RoomView>}} con cada cambio de la sala o de sus tableros.</li>
 *   <li>Cada cambio de la partida ({@link GameChanged}, tras el commit) se envía a todas sus conexiones, desde cualquier
 *       instancia: las conexiones viven en la base de datos.</li>
 *   <li>{@code {"action":"ping"}} mantiene viva la conexión (API Gateway corta a los 10 min sin tráfico).</li>
 * </ol>
 * Mensajes al cliente: {@code {"type":"game","game":<GameView>}} o {@code {"type":"error","error":"…"}}.
 * El long polling sigue funcionando en paralelo como respaldo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveConnections {

    private final WsConnectionRepository connections;
    private final LiveChannel channel;
    private final GameQueries games;
    private final RoomQueries rooms;
    private final JwtDecoder jwtDecoder;
    private final PlayerIdentityResolver identity;
    private final ObjectMapper json;
    private final Clock clock;

    /** Abre una conexión si el token es válido; si no, 401 (API Gateway rechaza la conexión). */
    @Transactional
    public void connect(String connectionId, String token) {
        if (connectionId == null || connectionId.isBlank()) {
            throw ApiException.badRequest("MISSING_CONNECTION", "Falta el id de la conexión");
        }
        Jwt jwt = decode(token);
        long playerId = identity.resolve(jwt.getSubject(), jwt.getClaims()).playerId();
        connections.save(new WsConnection(connectionId, playerId, clock.instant()));
        log.debug("Conexión en vivo {} del jugador {}", connectionId, playerId);
    }

    private Jwt decode(String token) {
        if (token == null || token.isBlank()) throw unauthorized();
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw unauthorized();
        }
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Se necesita una sesión válida para jugar en vivo");
    }

    /** Mensaje del cliente: suscribirse a una partida o ping. Acciones desconocidas se responden con un error. */
    @Transactional
    public void message(String connectionId, String body) {
        WsConnection c = connections.findById(connectionId)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_CONNECTION", "La conexión no existe"));
        c.setLastSeenAt(clock.instant());
        JsonNode msg = parse(body);
        switch (msg.path("action").asText()) {
            case "subscribe" -> {
                if (msg.has("roomId")) subscribeRoom(c, msg.path("roomId").asLong(0));
                else subscribe(c, msg.path("gameId").asLong(0));
            }
            case "ping" -> { /* solo actualiza last_seen_at */ }
            default -> sendError(connectionId, "UNKNOWN_ACTION");
        }
        connections.save(c);
    }

    private void subscribe(WsConnection c, long gameId) {
        if (gameId <= 0) {
            sendError(c.getConnectionId(), "MISSING_GAME");
            return;
        }
        GameView view = games.view(gameId); // 404 si no existe; mirar una partida es público
        c.setGameId(gameId);
        c.setRoomId(null);
        channel.send(c.getConnectionId(), gameMessage(view));
    }

    /** Una sala la ven solo su organizador y sus miembros: a otro se le responde con un error, sin suscribirlo. */
    private void subscribeRoom(WsConnection c, long roomId) {
        if (roomId <= 0 || !rooms.canView(c.getPlayerId(), roomId)) {
            sendError(c.getConnectionId(), roomId <= 0 ? "MISSING_ROOM" : "NOT_IN_ROOM");
            return;
        }
        c.setRoomId(roomId);
        c.setGameId(null);
        channel.send(c.getConnectionId(), roomMessage(rooms.view(c.getPlayerId(), roomId)));
    }

    @Transactional
    public void disconnect(String connectionId) {
        if (connectionId != null && connections.existsById(connectionId)) connections.deleteById(connectionId);
    }

    /** Envía el estado nuevo a todas las conexiones de la partida; borra las que ya no existen. */
    @Async("liveExecutor")
    @EventListener
    public void onGameChanged(GameChanged event) {
        broadcast(event.gameId());
    }

    @Transactional
    public void broadcast(long gameId) {
        var subscribers = connections.findByGameId(gameId);
        if (subscribers.isEmpty()) return;
        String payload = gameMessage(games.view(gameId));
        for (WsConnection c : subscribers) {
            if (!sendSafely(c.getConnectionId(), payload)) connections.deleteById(c.getConnectionId());
        }
    }

    /** Envía la sala a cada conexión que la sigue, con la vista de su jugador (su tablero, si es el organizador). */
    @Async("liveExecutor")
    @EventListener
    public void onRoomChanged(RoomChanged event) {
        broadcastRoom(event.roomId());
    }

    @Transactional
    public void broadcastRoom(long roomId) {
        for (WsConnection c : connections.findByRoomId(roomId)) {
            if (!rooms.canView(c.getPlayerId(), roomId)) { // lo sacaron de la sala: deja de recibirla
                c.setRoomId(null);
                connections.save(c);
                sendSafely(c.getConnectionId(), write(Map.of("type", "error", "error", "NOT_IN_ROOM")));
                continue;
            }
            String payload = roomMessage(rooms.view(c.getPlayerId(), roomId));
            if (!sendSafely(c.getConnectionId(), payload)) connections.deleteById(c.getConnectionId());
        }
    }

    /**
     * Un envío que falla no debe dejar sin el mensaje a las demás conexiones de la partida (p. ej. el ganador cierra su
     * socket al ver el mate justo cuando se envía el estado final al rival).
     */
    private boolean sendSafely(String connectionId, String payload) {
        try {
            return channel.send(connectionId, payload);
        } catch (RuntimeException e) {
            log.warn("Envío en vivo a {} falló: {}", connectionId, e.getMessage());
            return false;
        }
    }

    /** Conexiones sin actividad desde antes de {@code before} (API Gateway las corta a las 2 h como máximo). */
    @Transactional
    public int purgeSeenBefore(Instant before) {
        return connections.deleteSeenBefore(before);
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body == null ? "{}" : body);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("INVALID_MESSAGE", "El mensaje no es JSON");
        }
    }

    private String gameMessage(GameView view) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "game");
        msg.put("game", view);
        return write(msg);
    }

    private String roomMessage(RoomView view) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "room");
        msg.put("room", view);
        return write(msg);
    }

    private void sendError(String connectionId, String error) {
        channel.send(connectionId, write(Map.of("type", "error", "error", error)));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el mensaje en vivo", e);
        }
    }
}
