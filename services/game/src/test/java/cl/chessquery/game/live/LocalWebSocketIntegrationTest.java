package cl.chessquery.game.live;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.game.users.UsersClient;
import cl.chessquery.common.rating.PlatformRatings;
import cl.chessquery.game.users.UsersClient.PlayerSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El WebSocket local ({@code /ws}), con un servidor real y un cliente WebSocket real: lo que usa la web en local y el E2E. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class LocalWebSocketIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;
    @MockitoBean UsersClient users;
    @MockitoBean PlayerIdentityResolver identity;

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void mocks() {
        when(identity.resolve(anyString(), anyMap())).thenAnswer(inv ->
                new ResolvedIdentity(Long.parseLong(inv.getArgument(0, String.class).substring(4)), null));
        when(users.player(anyLong())).thenAnswer(inv ->
                new PlayerSummary(inv.getArgument(0, Long.class), "Jugador", "E2E", null, new PlatformRatings(null, null, 1500, null), null, null, true));
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            if (!token.startsWith("token-")) throw new BadJwtException("token inválido");
            return Jwt.withTokenValue(token).header("alg", "none").subject("sub-" + token.substring(6))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        });
    }

    /** Cliente que junta los mensajes recibidos y el motivo de cierre. */
    static final class Client extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }
    }

    private WebSocketSession open(Client client, String token) throws Exception {
        return new StandardWebSocketClient().execute(client, "ws://localhost:" + port + "/ws?token=" + token).get(5, TimeUnit.SECONDS);
    }

    @Test
    void elRivalRecibeLaJugadaPorElSocket() throws Exception {
        String created = mvc.perform(post("/api/games").with(jwt().jwt(j -> j.subject("sub-1")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"opponentId\":2,\"minutes\":5,\"color\":\"WHITE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long gameId = json.readTree(created).get("id").asLong();
        mvc.perform(post("/api/games/" + gameId + "/accept").with(jwt().jwt(j -> j.subject("sub-2")))).andExpect(status().isOk());

        Client luis = new Client();
        WebSocketSession session = open(luis, "token-2");
        session.sendMessage(new TextMessage("{\"action\":\"subscribe\",\"gameId\":" + gameId + "}"));
        assertThat(luis.messages.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"game\"").contains("\"ply\":0");

        mvc.perform(post("/api/games/" + gameId + "/moves").with(jwt().jwt(j -> j.subject("sub-1")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"uci\":\"e2e4\"}")).andExpect(status().isOk());
        assertThat(luis.messages.poll(5, TimeUnit.SECONDS)).contains("\"ply\":1").contains("\"e4\"");

        session.sendMessage(new TextMessage("{\"action\":\"subscribe\",\"gameId\":999999}"));
        assertThat(luis.messages.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"error\"").contains("GAME_NOT_FOUND");
        session.close();
    }

    @Test
    void sinTokenValidoSeCierraLaConexion() throws Exception {
        Client intruso = new Client();
        open(intruso, "falso");
        CloseStatus status = intruso.closed.get(5, TimeUnit.SECONDS);
        assertThat(status.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
        assertThat(status.getReason()).isEqualTo("INVALID_TOKEN");
    }
}
