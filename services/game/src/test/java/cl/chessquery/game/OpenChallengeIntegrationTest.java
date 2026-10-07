package cl.chessquery.game;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.common.rating.PlatformRatings;
import cl.chessquery.game.users.UsersClient;
import cl.chessquery.game.users.UsersClient.PlayerSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Desafío abierto contra PostgreSQL real: Ana publica un enlace, Luis (que no es su amigo) lo acepta y la partida
 * empieza ya en juego; nadie más puede tomarlo, vence si nadie entra y solo Ana lo cancela.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OpenChallengeIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class TestClock {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;
    @MockitoBean UsersClient users;
    @MockitoBean PlayerIdentityResolver identity;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;

    static final long ANA = 1, LUIS = 2, OTRO = 3;

    @BeforeEach
    void mocks() {
        when(identity.resolve(anyString(), anyMap())).thenAnswer(inv ->
                new ResolvedIdentity(Long.parseLong(inv.getArgument(0, String.class).substring(4)), null));
        when(users.player(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0, Long.class);
            // Ana juega relámpago en ChessQuery; Luis es nuevo (sin ELO de plataforma ni nacional)
            return new PlayerSummary(id, id == ANA ? "Ana" : "Luis", id == ANA ? "Soto" : "P.", null,
                    id == ANA ? new PlatformRatings(null, 1650, null, null) : null, null, null, true);
        });
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long playerId) {
        return b.with(jwt().jwt(j -> j.subject("sub-" + playerId)));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String publish(long player, String body) throws Exception {
        return body(mvc.perform(as(post("/api/games/open"), player).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())).get("token").asText();
    }

    @Test
    void unDesconocidoAceptaElEnlaceYLaPartidaEmpieza() throws Exception {
        String token = publish(ANA, "{\"minutes\":3,\"incrementSeconds\":2,\"color\":\"WHITE\"}");
        assertThat(token).hasSize(22).matches("[A-Za-z0-9_-]+"); // 128 bits en base64url

        mvc.perform(as(get("/api/games/open"), ANA)).andExpect(jsonPath("$[0].token").value(token));
        mvc.perform(as(get("/api/games/open/" + token), LUIS))
           .andExpect(jsonPath("$.challengerName").value("Ana Soto"))
           .andExpect(jsonPath("$.category").value("BLITZ"))
           .andExpect(jsonPath("$.status").value("OPEN"))
           .andExpect(jsonPath("$.rated").value(true))
           .andExpect(jsonPath("$.mine").value(false));
        mvc.perform(as(post("/api/games/open/" + token + "/accept"), ANA)).andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("SELF_CHALLENGE"));

        long gameId = body(mvc.perform(as(post("/api/games/open/" + token + "/accept"), LUIS))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.white.playerId").value(ANA))
                .andExpect(jsonPath("$.white.ratingBefore").value(1650))
                .andExpect(jsonPath("$.black.playerId").value(LUIS))
                .andExpect(jsonPath("$.black.ratingBefore").value(1500)) // nuevo: parte de 1500
                .andExpect(jsonPath("$.rated").value(true))).get("id").asLong();

        // Nadie más lo toma; quien desafió ve a qué partida ir
        mvc.perform(as(post("/api/games/open/" + token + "/accept"), OTRO)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("CHALLENGE_TAKEN"));
        mvc.perform(as(get("/api/games/open/" + token), ANA)).andExpect(jsonPath("$.status").value("ACCEPTED"))
           .andExpect(jsonPath("$.gameId").value(gameId)).andExpect(jsonPath("$.mine").value(true));
        mvc.perform(as(get("/api/games/open"), ANA)).andExpect(jsonPath("$.length()").value(0));
        // Se juega con los endpoints de siempre
        mvc.perform(as(post("/api/games/" + gameId + "/moves"), ANA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"uci\":\"e2e4\"}")).andExpect(status().isOk());
    }

    @Test
    void venceSeCancelaYTieneLimites() throws Exception {
        String vence = publish(LUIS, "{\"minutes\":10,\"incrementSeconds\":0}");
        clock.advance(Duration.ofMinutes(31));
        mvc.perform(as(get("/api/games/open/" + vence), ANA)).andExpect(jsonPath("$.status").value("EXPIRED"))
           .andExpect(jsonPath("$.color").value("RANDOM"));
        mvc.perform(as(post("/api/games/open/" + vence + "/accept"), ANA)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("CHALLENGE_EXPIRED"));

        String cancelar = publish(LUIS, "{\"minutes\":5,\"incrementSeconds\":0,\"rated\":false}");
        mvc.perform(as(delete("/api/games/open/" + cancelar), ANA)).andExpect(status().isForbidden())
           .andExpect(jsonPath("$.error").value("NOT_YOUR_CHALLENGE"));
        mvc.perform(as(delete("/api/games/open/" + cancelar), LUIS)).andExpect(status().isNoContent());
        mvc.perform(as(post("/api/games/open/" + cancelar + "/accept"), ANA)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("CHALLENGE_TAKEN"));
        mvc.perform(as(get("/api/games/open/no-existe"), ANA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/games/open/" + cancelar)).andExpect(status().isUnauthorized());

        mvc.perform(as(post("/api/games/open"), LUIS).contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":0,\"incrementSeconds\":0}"))
           .andExpect(status().isBadRequest());
        for (int i = 0; i < 3; i++) publish(OTRO, "{\"minutes\":1,\"incrementSeconds\":0}");
        mvc.perform(as(post("/api/games/open"), OTRO).contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":1,\"incrementSeconds\":0}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("TOO_MANY_OPEN_CHALLENGES"));
    }
}
