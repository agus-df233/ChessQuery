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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El camino de la nube: API Gateway WebSocket llama a {@code /internal/ws/*} y las respuestas salen por el canal
 * (aquí simulado). Contra PostgreSQL real: conectar con token, suscribirse, recibir cada jugada y limpiar conexiones.
 */
@SpringBootTest(properties = {"chessquery.live.mode=apigateway", "chessquery.live.management-endpoint=http://localhost:1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class LiveApiGatewayIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;
    @MockitoBean UsersClient users;
    @MockitoBean PlayerIdentityResolver identity;
    @MockitoBean LiveChannel channel;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired WsConnectionRepository connections;
    @Autowired LiveConnections live;

    static final long ANA = 1, LUIS = 2, TERCERO = 3, PROFE = 9;

    @BeforeEach
    void mocks() {
        clearInvocations(channel);
        when(channel.send(anyString(), anyString())).thenReturn(true);
        when(identity.resolve(anyString(), anyMap())).thenAnswer(inv -> {
            long id = Long.parseLong(inv.getArgument(0, String.class).substring(4));
            return new ResolvedIdentity(id, id == PROFE ? Long.valueOf(5) : null); // PROFE organiza el club 5
        });
        when(users.player(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0, Long.class);
            return new PlayerSummary(id, id == ANA ? "Ana" : "Luis", "E2E", null, new PlatformRatings(null, null, 1500, null), null, null, true);
        });
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            if (!token.startsWith("token-")) throw new BadJwtException("token inválido");
            return Jwt.withTokenValue(token).header("alg", "none").subject("sub-" + token.substring(6))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        });
    }

    /** Como API Gateway: token interno + id de la conexión (+ token del jugador al conectar). */
    private static MockHttpServletRequestBuilder gateway(String path, String connectionId) {
        return post("/internal/ws/" + path).header("X-Internal-Token", "test-token").header("X-Connection-Id", connectionId);
    }

    private long startGame() throws Exception {
        String created = mvc.perform(post("/api/games").with(jwt().jwt(j -> j.subject("sub-" + ANA)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"opponentId\":2,\"minutes\":5,\"color\":\"WHITE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        mvc.perform(post("/api/games/" + id + "/accept").with(jwt().jwt(j -> j.subject("sub-" + LUIS)))).andExpect(status().isOk());
        return id;
    }

    @Test
    void conectarExigeTokenValidoYElTokenInterno() throws Exception {
        mvc.perform(post("/internal/ws/connect").header("X-Connection-Id", "c0").header("X-Ws-Token", "token-1"))
           .andExpect(status().isUnauthorized()); // sin X-Internal-Token no es API Gateway
        mvc.perform(gateway("connect", "c1")).andExpect(status().isUnauthorized());
        mvc.perform(gateway("connect", "c2").header("X-Ws-Token", "falso"))
           .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
        mvc.perform(gateway("connect", "c3").header("X-Ws-Token", "token-1")).andExpect(status().isOk());
        assertThat(connections.findById("c3")).get().extracting(WsConnection::getPlayerId).isEqualTo(ANA);
        mvc.perform(gateway("message", "desconocida").content("{\"action\":\"ping\"}")).andExpect(status().isNotFound());
    }

    @Test
    void suscritoRecibeElEstadoYCadaJugada() throws Exception {
        long gameId = startGame();
        mvc.perform(gateway("connect", "luis-1").header("X-Ws-Token", "token-2")).andExpect(status().isOk());
        mvc.perform(gateway("message", "luis-1").content("{\"action\":\"subscribe\",\"gameId\":" + gameId + "}"))
           .andExpect(status().isOk());
        verify(channel).send(eq("luis-1"), argThat(m -> m.contains("\"type\":\"game\"") && m.contains("\"ply\":0")));

        mvc.perform(post("/api/games/" + gameId + "/moves").with(jwt().jwt(j -> j.subject("sub-" + ANA)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"uci\":\"e2e4\"}")).andExpect(status().isOk());
        // Se envía después del commit, en otro hilo
        verify(channel, timeout(5000)).send(eq("luis-1"), argThat(m -> m.contains("\"ply\":1") && m.contains("\"e4\"")));

        mvc.perform(gateway("message", "luis-1").content("{\"action\":\"ping\"}")).andExpect(status().isOk());
        mvc.perform(gateway("message", "luis-1").content("{\"action\":\"otra\"}")).andExpect(status().isOk());
        verify(channel).send(eq("luis-1"), argThat(m -> m.contains("UNKNOWN_ACTION")));
        mvc.perform(gateway("message", "luis-1").content("{\"action\":\"subscribe\"}")).andExpect(status().isOk());
        verify(channel).send(eq("luis-1"), argThat(m -> m.contains("MISSING_GAME")));
        mvc.perform(gateway("message", "luis-1").content("no es json")).andExpect(status().isBadRequest());
        mvc.perform(gateway("message", "luis-1").content("{\"action\":\"subscribe\",\"gameId\":999999}"))
           .andExpect(status().isNotFound());
    }

    @Test
    void conexionesQueYaNoExistenSeBorran() throws Exception {
        long gameId = startGame();
        mvc.perform(gateway("connect", "ida").header("X-Ws-Token", "token-1")).andExpect(status().isOk());
        mvc.perform(gateway("message", "ida").content("{\"action\":\"subscribe\",\"gameId\":" + gameId + "}")).andExpect(status().isOk());
        when(channel.send(eq("ida"), anyString())).thenReturn(false); // API Gateway respondió 410 Gone
        live.broadcast(gameId);
        assertThat(connections.findById("ida")).isEmpty();

        mvc.perform(gateway("connect", "vieja").header("X-Ws-Token", "token-2")).andExpect(status().isOk());
        mvc.perform(gateway("disconnect", "vieja")).andExpect(status().isOk());
        assertThat(connections.findById("vieja")).isEmpty();
        mvc.perform(gateway("disconnect", "vieja")).andExpect(status().isOk()); // repetir no falla

        mvc.perform(gateway("connect", "olvidada").header("X-Ws-Token", "token-2")).andExpect(status().isOk());
        assertThat(live.purgeSeenBefore(Instant.now().plusSeconds(60))).isGreaterThanOrEqualTo(1);
        assertThat(connections.findById("olvidada")).isEmpty();
    }

    private String api(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, long player, String body)
            throws Exception {
        return mvc.perform(b.with(jwt().jwt(j -> j.subject("sub-" + player))).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    /** La cuadrícula de una sala por WebSocket: solo su organizador y sus miembros, con cada jugada de sus tableros. */
    @Test
    void salaPorWebSocketSoloParaSuOrganizadorYMiembros() throws Exception {
        var room = json.readTree(api(post("/api/rooms"), PROFE,
                "{\"name\":\"Clase\",\"boards\":1,\"maxPlayers\":3,\"minutes\":5,\"incrementSeconds\":0}"));
        long roomId = room.get("id").asLong();
        String join = "{\"code\":\"" + room.get("code").asText() + "\"}";
        for (long p : new long[] {ANA, LUIS, TERCERO}) api(post("/api/rooms/join"), p, join);
        api(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/rooms/" + roomId + "/boards/1"),
                PROFE, "{\"whitePlayerId\":1,\"blackPlayerId\":2}");
        long gameId = json.readTree(api(post("/api/rooms/" + roomId + "/boards/1/start"), PROFE, ""))
                .at("/boards/0/game/id").asLong();

        mvc.perform(gateway("connect", "profe").header("X-Ws-Token", "token-9")).andExpect(status().isOk());
        mvc.perform(gateway("message", "profe").content("{\"action\":\"subscribe\",\"roomId\":" + roomId + "}"))
           .andExpect(status().isOk());
        verify(channel).send(eq("profe"), argThat(m -> m.contains("\"type\":\"room\"") && m.contains("\"organizer\":true")));
        mvc.perform(gateway("message", "profe").content("{\"action\":\"subscribe\",\"roomId\":0}")).andExpect(status().isOk());
        verify(channel).send(eq("profe"), argThat(m -> m.contains("MISSING_ROOM")));
        mvc.perform(gateway("message", "profe").content("{\"action\":\"subscribe\",\"roomId\":" + roomId + "}"))
           .andExpect(status().isOk());

        // Alguien que no entró a la sala no la recibe
        mvc.perform(gateway("connect", "intruso").header("X-Ws-Token", "token-7")).andExpect(status().isOk());
        mvc.perform(gateway("message", "intruso").content("{\"action\":\"subscribe\",\"roomId\":" + roomId + "}"))
           .andExpect(status().isOk());
        verify(channel).send(eq("intruso"), argThat(m -> m.contains("NOT_IN_ROOM")));
        assertThat(connections.findById("intruso")).get().extracting(WsConnection::getRoomId).isNull();

        // Una jugada en el tablero llega a la cuadrícula del organizador (después del commit, en otro hilo)
        clearInvocations(channel);
        mvc.perform(post("/api/games/" + gameId + "/moves").with(jwt().jwt(j -> j.subject("sub-" + ANA)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"uci\":\"e2e4\"}")).andExpect(status().isOk());
        verify(channel, timeout(5000).atLeastOnce())
                .send(eq("profe"), argThat(m -> m.contains("\"type\":\"room\"") && m.contains("\"e2e4\"")));

        // Un espectador que se va deja de recibir la sala
        mvc.perform(gateway("connect", "tercero").header("X-Ws-Token", "token-3")).andExpect(status().isOk());
        mvc.perform(gateway("message", "tercero").content("{\"action\":\"subscribe\",\"roomId\":" + roomId + "}"))
           .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/rooms/" + roomId + "/members/3")
                .with(jwt().jwt(j -> j.subject("sub-3")))).andExpect(status().isNoContent());
        verify(channel, timeout(5000)).send(eq("tercero"), argThat(m -> m.contains("NOT_IN_ROOM")));
    }

    /** Regresión (E2E del 30-09): el ganador cierra su socket al ver el mate y el envío a él falla. */
    @Test
    void unEnvioQueFallaNoDejaSinMensajeAlRival() throws Exception {
        long gameId = startGame();
        for (String[] c : new String[][] {{"ganadora", "token-1"}, {"rival", "token-2"}}) {
            mvc.perform(gateway("connect", c[0]).header("X-Ws-Token", c[1])).andExpect(status().isOk());
            mvc.perform(gateway("message", c[0]).content("{\"action\":\"subscribe\",\"gameId\":" + gameId + "}"))
               .andExpect(status().isOk());
        }
        when(channel.send(eq("ganadora"), anyString())).thenThrow(new IllegalStateException("sesión cerrada"));
        clearInvocations(channel);
        live.broadcast(gameId);
        verify(channel).send(eq("rival"), argThat(m -> m.contains("\"type\":\"game\"")));
        assertThat(connections.findById("ganadora")).isEmpty();
        assertThat(connections.findById("rival")).isPresent();
    }
}
