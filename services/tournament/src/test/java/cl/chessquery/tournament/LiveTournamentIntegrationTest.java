package cl.chessquery.tournament;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.common.rating.PlatformRatings;
import cl.chessquery.tournament.users.UsersClient;
import cl.chessquery.tournament.users.UsersClient.PlayerSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tiempo real del torneo para la pantalla de la sala (sin login): cada cambio sube la versión y despierta al long
 * polling. Incluye el caso delicado: editar el torneo (Hibernate guarda la entidad) no debe pisar la versión.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class LiveTournamentIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;
    @MockitoBean UsersClient users;
    @MockitoBean PlayerIdentityResolver identity;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    static final long ORGANIZER = 1, ORG_ID = 10;

    @BeforeEach
    void mocks() {
        when(identity.resolve(anyString(), anyMap())).thenAnswer(inv -> {
            long id = Long.parseLong(inv.getArgument(0, String.class).substring(4));
            return new ResolvedIdentity(id, id == ORGANIZER ? Long.valueOf(ORG_ID) : null);
        });
        when(users.planOf(anyLong())).thenReturn(new UsersClient.PlanInfo(ORG_ID, "FREE", 3));
        when(users.player(anyLong())).thenAnswer(inv -> summary(inv.getArgument(0, Long.class)));
        when(users.players(any())).thenAnswer(inv -> ((Collection<Long>) inv.getArgument(0)).stream()
                .map(LiveTournamentIntegrationTest::summary).toList());
    }

    static PlayerSummary summary(long id) {
        return new PlayerSummary(id, "Jugador" + id, "Apellido" + id, "Apellido" + id, null, null, 1500, null,
                new PlatformRatings(null, null, 1500 + (int) id, null), null, null, 2000, "M", false, null, true);
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long playerId) {
        return b.with(jwt().jwt(j -> j.subject("sub-" + playerId)));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long version(long id) throws Exception {
        return body(mvc.perform(get("/api/public/tournaments/" + id + "/live"))).get("version").asLong();
    }

    @Test
    void cadaCambioSubeLaVersionYDespiertaALaPantalla() throws Exception {
        String req = "{\"name\":\"Rápido del sábado\",\"startDate\":\"2026-11-01\",\"format\":\"SWISS\",\"rounds\":2}";
        long id = body(mvc.perform(as(post("/api/tournaments"), ORGANIZER).contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated())).at("/tournament/id").asLong();
        mvc.perform(get("/api/public/tournaments/" + id + "/live")) // sin login
           .andExpect(jsonPath("$.detail.tournament.name").value("Rápido del sábado"))
           .andExpect(jsonPath("$.rounds.length()").value(0)).andExpect(jsonPath("$.standings.length()").value(0));
        mvc.perform(get("/api/public/tournaments/999999/live")).andExpect(status().isNotFound());

        // La pantalla espera; una inscripción la despierta con la versión nueva y el inscrito
        long v0 = version(id);
        MvcResult waiting = mvc.perform(get("/api/public/tournaments/" + id + "/live").param("afterVersion", String.valueOf(v0)))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations"), ORGANIZER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":11}")).andExpect(status().isOk());
        JsonNode woke = json.readTree(mvc.perform(asyncDispatch(waiting)).andReturn().getResponse().getContentAsString());
        assertThat(woke.get("version").asLong()).isGreaterThan(v0);
        assertThat(woke.at("/detail/players").size()).isEqualTo(1);

        // Editar el torneo guarda la entidad: la versión sube igual (no se pisa con la que tenía al cargarlo)
        long beforeEdit = version(id);
        mvc.perform(as(put("/api/tournaments/" + id), ORGANIZER).contentType(MediaType.APPLICATION_JSON)
                .content(req.replace("Rápido del sábado", "Rápido del domingo"))).andExpect(status().isOk());
        assertThat(version(id)).isGreaterThan(beforeEdit);

        // Con una versión vieja responde de inmediato (como DeferredResult: igual pasa por el despacho asíncrono)
        MvcResult immediate = mvc.perform(get("/api/public/tournaments/" + id + "/live").param("afterVersion", "0")).andReturn();
        mvc.perform(asyncDispatch(immediate)).andExpect(jsonPath("$.detail.tournament.name").value("Rápido del domingo"));

        // Ronda y resultado: la tabla aparece y cada carga sube la versión
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations"), ORGANIZER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":12}")).andExpect(status().isOk());
        mvc.perform(as(post("/api/tournaments/" + id + "/rounds"), ORGANIZER)).andExpect(status().isCreated());
        long beforeResult = version(id);
        mvc.perform(as(put("/api/tournaments/" + id + "/rounds/1/boards/1"), ORGANIZER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"DRAW\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/public/tournaments/" + id + "/live"))
           .andExpect(jsonPath("$.rounds[0].boards[0].resultLabel").value("½-½"))
           .andExpect(jsonPath("$.standings.length()").value(2))
           .andExpect(jsonPath("$.version").value(beforeResult + 1));
    }
}
