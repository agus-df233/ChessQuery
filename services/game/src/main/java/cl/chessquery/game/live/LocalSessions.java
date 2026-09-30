package cl.chessquery.game.live;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Canal local: las sesiones WebSocket abiertas en {@code /ws} de esta instancia (desarrollo y E2E). */
@Slf4j
public class LocalSessions implements LiveChannel {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    void add(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    void remove(String id) {
        sessions.remove(id);
    }

    @Override
    public boolean send(String connectionId, String json) {
        WebSocketSession session = sessions.get(connectionId);
        if (session == null || !session.isOpen()) return false;
        try {
            synchronized (session) { // una sesión no admite envíos concurrentes
                session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            // IllegalStateException: la sesión se cerró entre el isOpen() y el envío (p. ej. el cliente cerró al
            // ver el fin de la partida). No es un error: la conexión ya no existe.
            log.debug("Sesión local {} cerrada al enviar: {}", connectionId, e.getMessage());
            return false;
        }
    }
}
