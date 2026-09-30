package cl.chessquery.game.api;

import cl.chessquery.game.live.LiveConnections;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Lo que llama API Gateway WebSocket en la nube (vía el ALB). Bajo {@code /internal}: exige {@code X-Internal-Token}
 * (lo agrega la integración de API Gateway) y el ALB exige la cabecera de origen. API Gateway manda el id de la
 * conexión en {@code X-Connection-Id} y el token del jugador (query {@code token} al conectar) en {@code X-Ws-Token}.
 */
@RestController
@RequestMapping("/internal/ws")
@RequiredArgsConstructor
public class LiveWsController {

    private final LiveConnections live;

    /** 200 acepta la conexión; 401 (token inválido) hace que API Gateway la rechace. */
    @PostMapping("/connect")
    public void connect(@RequestHeader("X-Connection-Id") String connectionId,
                        @RequestHeader(value = "X-Ws-Token", required = false) String token) {
        live.connect(connectionId, token);
    }

    @PostMapping("/message")
    public void message(@RequestHeader("X-Connection-Id") String connectionId, @RequestBody(required = false) String body) {
        live.message(connectionId, body);
    }

    @PostMapping("/disconnect")
    public void disconnect(@RequestHeader("X-Connection-Id") String connectionId) {
        live.disconnect(connectionId);
    }
}
