package cl.chessquery.game.live;

import cl.chessquery.common.api.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * WebSocket nativo en {@code /ws} para desarrollo y E2E: traduce la conexión, los mensajes y el cierre a
 * {@link LiveConnections}, igual que lo hace API Gateway en la nube con {@code /internal/ws/*}.
 */
@RequiredArgsConstructor
public class LocalWebSocketHandler extends TextWebSocketHandler {

    private final LiveConnections live;
    private final LocalSessions sessions;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = session.getUri() == null ? null
                : UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("token");
        try {
            live.connect(session.getId(), token);
            sessions.add(session);
        } catch (ApiException e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason(e.getError()));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            live.message(session.getId(), message.getPayload());
        } catch (ApiException e) {
            sessions.send(session.getId(), "{\"type\":\"error\",\"error\":\"" + e.getError() + "\"}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        live.disconnect(session.getId());
    }
}
