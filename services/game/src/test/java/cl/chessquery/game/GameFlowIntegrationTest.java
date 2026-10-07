package cl.chessquery.game;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.game.events.GameEvents;
import cl.chessquery.game.users.UsersClient;
import cl.chessquery.common.rating.PlatformRatings;
import cl.chessquery.game.users.UsersClient.PlayerSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partida de punta a punta contra PostgreSQL real: A desafía a B, B acepta, juegan hasta el mate con el reloj
 * del servidor, se publica el rating y cualquiera puede ver la partida y bajar el PGN. También tiempo agotado,
 * tablas, abandono, desafíos rechazados/cancelados/vencidos y long polling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class GameFlowIntegrationTest {

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
    @Autowired GameSweeper sweeper;

    static final long ANA = 1, LUIS = 2, SIN_CUENTA = 3;

    @BeforeEach
    void mocks() {
        clearInvocations(events);
        when(identity.resolve(anyString(), anyMap())).thenAnswer(inv ->
                new ResolvedIdentity(Long.parseLong(inv.getArgument(0, String.class).substring(4)), null));
        when(users.player(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0, Long.class);
            // Ana tiene ELO ChessQuery solo en relámpago; Luis no tiene ninguno (empieza con su ELO nacional)
            return new PlayerSummary(id, id == ANA ? "Ana" : "Luis", id == ANA ? "Soto" : "P.", null,
                    id == ANA ? new PlatformRatings(null, 1500, null, null) : null, id == LUIS ? 1600 : null, null,
                    id != SIN_CUENTA);
        });
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long playerId) {
        return b.with(jwt().jwt(j -> j.subject("sub-" + playerId)));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long startGame(String color, int minutes, int increment) throws Exception {
        long id = body(mvc.perform(as(post("/api/games"), ANA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"opponentId\":2,\"minutes\":" + minutes + ",\"incrementSeconds\":" + increment + ",\"color\":\"" + color + "\"}"))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(as(post("/api/games/" + id + "/accept"), LUIS)).andExpect(jsonPath("$.status").value("ACTIVE"));
        return id;
    }

    private ResultActions move(long id, long player, String uci) throws Exception {
        return mvc.perform(as(post("/api/games/" + id + "/moves"), player).contentType(MediaType.APPLICATION_JSON)
                .content("{\"uci\":\"" + uci + "\"}"));
    }

    @Test
    void desafioPartidaHastaElMateConRelojYRating() throws Exception {
        mvc.perform(as(post("/api/games"), ANA).contentType(MediaType.APPLICATION_JSON).content("{\"opponentId\":1,\"minutes\":5}"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("SELF_CHALLENGE"));
        mvc.perform(as(post("/api/games"), ANA).contentType(MediaType.APPLICATION_JSON).content("{\"opponentId\":3,\"minutes\":5}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OPPONENT_WITHOUT_ACCOUNT"));

        long id = body(mvc.perform(as(post("/api/games"), ANA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"opponentId\":2,\"minutes\":3,\"incrementSeconds\":2,\"color\":\"WHITE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.white.name").value("Ana Soto"))
                .andExpect(jsonPath("$.black.name").value("Luis P."))
                .andExpect(jsonPath("$.black.ratingBefore").value(1600))).get("id").asLong();
        mvc.perform(as(get("/api/games/mine"), LUIS)).andExpect(jsonPath("$.incoming[0].id").value(id));
        mvc.perform(as(get("/api/games/mine"), ANA)).andExpect(jsonPath("$.outgoing[0].id").value(id));
        mvc.perform(as(post("/api/games/" + id + "/accept"), ANA)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/games/" + id + "/accept"), 9)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/games/" + id + "/accept"), LUIS)).andExpect(jsonPath("$.status").value("ACTIVE"));

        // Long polling: Luis espera la jugada de Ana
        long version = body(mvc.perform(get("/api/public/games/" + id))).get("version").asLong();
        MvcResult waiting = mvc.perform(as(get("/api/games/" + id).param("afterVersion", String.valueOf(version)), LUIS))
                .andExpect(request().asyncStarted()).andReturn();

        clock.advance(Duration.ofSeconds(10));
        move(id, LUIS, "e7e5").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NOT_YOUR_TURN"));
        move(id, ANA, "e2e5").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("ILLEGAL_MOVE"));
        move(id, ANA, "e2e4").andExpect(status().isOk())
           .andExpect(jsonPath("$.white.clockMs").value(180_000 - 10_000 + 2_000))
           .andExpect(jsonPath("$.category").value("BLITZ")) // 3+2
           .andExpect(jsonPath("$.sideToMove").value("BLACK"));
        mvc.perform(asyncDispatch(waiting)).andExpect(jsonPath("$.san[0]").value("e4"));

        clock.advance(Duration.ofSeconds(30));
        mvc.perform(get("/api/public/games/" + id)).andExpect(jsonPath("$.black.clockMs").value(150_000));
        for (String[] m : new String[][] {{"2", "e7e5"}, {"1", "f1c4"}, {"2", "b8c6"}, {"1", "d1h5"}, {"2", "g8f6"}}) {
            move(id, Long.parseLong(m[0]), m[1]).andExpect(status().isOk());
        }
        move(id, ANA, "h5f7").andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("FINISHED"))
           .andExpect(jsonPath("$.result").value("WHITE_WINS"))
           .andExpect(jsonPath("$.termination").value("CHECKMATE"))
           .andExpect(jsonPath("$.terminationLabel").value("jaque mate"))
           .andExpect(jsonPath("$.white.ratingAfter").value(1500 + 13))
           .andExpect(jsonPath("$.black.ratingAfter").value(1600 - 26)); // Luis sin rating de plataforma: K = 40

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> elo = ArgumentCaptor.forClass(Map.class);
        verify(events, times(2)).publish(eq(GameEvents.ELO_UPDATED), elo.capture());
        assertThat(elo.getAllValues()).allSatisfy(p ->
                assertThat(p).containsEntry("ratingType", "PLATFORM_BLITZ").containsEntry("source", "GAME").containsEntry("gameId", id));
        verify(events).publish(eq(GameEvents.GAME_FINISHED), org.mockito.ArgumentMatchers.any());

        move(id, LUIS, "a7a6").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NOT_ACTIVE"));
        String pgn = mvc.perform(get("/api/public/games/" + id + "/pgn")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(pgn).contains("[White \"Ana Soto\"]").contains("Qxf7#").contains("1-0").contains("[Termination \"jaque mate\"]");
        mvc.perform(get("/api/public/games")).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(as(get("/api/games/mine"), ANA)).andExpect(jsonPath("$.finished[0].id").value(id));
        // Una partida terminada responde el long poll al instante
        mvc.perform(as(get("/api/games/" + id).param("afterVersion", "999"), ANA)).andExpect(request().asyncStarted());
    }

    @Test
    void elRelojLlegaACeroYElBarridoCierraLaPartida() throws Exception {
        long id = startGame("BLACK", 1, 0); // Ana con negras, Luis (blancas) no juega
        clock.advance(Duration.ofSeconds(61));
        sweeper.sweep();
        mvc.perform(get("/api/public/games/" + id))
           .andExpect(jsonPath("$.status").value("FINISHED"))
           .andExpect(jsonPath("$.result").value("BLACK_WINS"))
           .andExpect(jsonPath("$.termination").value("TIMEOUT"))
           .andExpect(jsonPath("$.white.clockMs").value(0))
           // Sin jugadas no se mueve el rating
           .andExpect(jsonPath("$.white.ratingAfter").doesNotExist());

        // Jugar con el tiempo vencido (antes del barrido) también cierra por tiempo
        long id2 = startGame("WHITE", 1, 0);
        clock.advance(Duration.ofSeconds(90));
        move(id2, ANA, "e2e4").andExpect(jsonPath("$.termination").value("TIMEOUT")).andExpect(jsonPath("$.result").value("BLACK_WINS"));
    }

    @Test
    void tablasAbandonoYDesafiosQueNoSeJuegan() throws Exception {
        long id = startGame("WHITE", 5, 0);
        move(id, ANA, "e2e4").andExpect(status().isOk());
        mvc.perform(as(post("/api/games/" + id + "/draw/accept"), LUIS)).andExpect(status().isConflict());
        mvc.perform(as(post("/api/games/" + id + "/draw/offer"), ANA)).andExpect(jsonPath("$.drawOfferBy").value(ANA));
        mvc.perform(as(post("/api/games/" + id + "/draw/decline"), LUIS)).andExpect(jsonPath("$.drawOfferBy").doesNotExist());
        mvc.perform(as(post("/api/games/" + id + "/draw/offer"), ANA)).andExpect(status().isOk());
        move(id, LUIS, "e7e5").andExpect(jsonPath("$.drawOfferBy").doesNotExist()); // jugar rechaza la oferta
        mvc.perform(as(post("/api/games/" + id + "/draw/offer"), LUIS)).andExpect(status().isOk());
        mvc.perform(as(post("/api/games/" + id + "/draw/offer"), ANA)) // ofrecer cuando el rival ofreció = aceptar
           .andExpect(jsonPath("$.result").value("DRAW")).andExpect(jsonPath("$.termination").value("AGREEMENT"));

        long id2 = startGame("WHITE", 5, 0);
        mvc.perform(as(post("/api/games/" + id2 + "/resign"), ANA))
           .andExpect(jsonPath("$.result").value("BLACK_WINS")).andExpect(jsonPath("$.termination").value("RESIGNATION"));
        mvc.perform(get("/api/public/games/" + id2 + "/pgn")).andExpect(status().isOk());

        long declined = body(mvc.perform(as(post("/api/games"), ANA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"opponentId\":2,\"minutes\":5,\"color\":\"RANDOM\",\"rated\":false}"))).get("id").asLong();
        mvc.perform(as(post("/api/games/" + declined + "/cancel"), LUIS)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/games/" + declined + "/decline"), LUIS)).andExpect(jsonPath("$.status").value("DECLINED"));
        mvc.perform(as(post("/api/games/" + declined + "/accept"), LUIS)).andExpect(jsonPath("$.error").value("NOT_PENDING"));

        long cancelled = body(mvc.perform(as(post("/api/games"), ANA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"opponentId\":2,\"minutes\":5}"))).get("id").asLong();
        mvc.perform(as(post("/api/games/" + cancelled + "/cancel"), ANA)).andExpect(jsonPath("$.status").value("CANCELLED"));

        long expired = body(mvc.perform(as(post("/api/games"), ANA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"opponentId\":2,\"minutes\":5}"))).get("id").asLong();
        clock.advance(Duration.ofMinutes(11));
        sweeper.sweep();
        mvc.perform(get("/api/public/games/" + expired)).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/public/games/" + expired + "/pgn")).andExpect(status().isConflict());
        mvc.perform(get("/api/public/games/999999")).andExpect(status().isNotFound());
        assertThat(List.of(id, id2)).doesNotContainNull();
    }
}
