package cl.chessquery.game.live;

/**
 * Envía un mensaje a una conexión en vivo. Implementaciones: {@link ApiGatewayLiveChannel} (nube, API Gateway
 * WebSocket) y {@link LocalSessions} (desarrollo y E2E, WebSocket nativo en {@code /ws}).
 */
public interface LiveChannel {

    /** @return false si la conexión ya no existe (se borra); true si se envió o si el fallo fue transitorio. */
    boolean send(String connectionId, String json);
}
