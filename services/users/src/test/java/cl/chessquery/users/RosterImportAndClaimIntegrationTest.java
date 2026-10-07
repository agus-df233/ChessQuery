package cl.chessquery.users;

import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roster contra PostgreSQL real: carga masiva con informe por fila (creados, duplicados y errores) e invitación para
 * que el jugador real reclame su perfil con su propia cuenta (otro email), llevándose sus ratings y sus torneos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RosterImportAndClaimIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlayerRepository players;

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String subject, String first) {
        return b.with(jwt().jwt(j -> j.subject(subject).claim("email", subject + "@real.cl").claim("iss", "http://localhost/test-issuer")
                .claim("given_name", first).claim("family_name", "Real")));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpServletRequestBuilder b, String subject, String content) throws Exception {
        return mvc.perform(as(b, subject, "Profe").contentType(MediaType.APPLICATION_JSON).content(content));
    }

    @Test
    void cargaMasivaEInvitacionParaReclamarElPerfil() throws Exception {
        send(post("/api/organizations"), "sub-profe", "{\"name\":\"Colegio Andino\"}").andExpect(status().isCreated());
        send(post("/api/organizations/me/roster/import"), "sub-nadie", "{\"rows\":[{\"firstName\":\"X\",\"lastName\":\"Y\"}]}")
           .andExpect(status().isForbidden());
        send(post("/api/organizations/me/roster/import"), "sub-profe", "{\"rows\":[]}").andExpect(status().isBadRequest());

        // Informe por fila: creada, inválida, creada, duplicada por email
        JsonNode report = body(send(post("/api/organizations/me/roster/import"), "sub-profe", """
                {"rows":[{"firstName":"Bruno","lastName":"Alumno","email":"bruno@colegio.cl","eloNational":1400},
                         {"firstName":"","lastName":"SinNombre"},
                         {"firstName":"Carla","lastName":"Alumna"},
                         {"firstName":"Bruno","lastName":"Repetido","email":"BRUNO@colegio.cl"}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2)).andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.errors").value(1))
                .andExpect(jsonPath("$.rows[1].error").value("INVALID_ROW"))
                .andExpect(jsonPath("$.rows[3].outcome").value("DUPLICATE")));
        long bruno = report.at("/rows/0/playerId").asLong();
        long carla = report.at("/rows/2/playerId").asLong();

        // Carla jugó torneos del colegio: tiene ELO ChessQuery rápido en su perfil del roster
        Player roster = players.findById(carla).orElseThrow();
        roster.setEloPlatformRapid(1620);
        players.save(roster);

        // Invitación: solo para un jugador del propio roster; repetirla devuelve la misma
        String token = body(mvc.perform(as(post("/api/organizations/me/roster/" + carla + "/invite"), "sub-profe", "Profe"))
                .andExpect(status().isOk())).get("token").asText();
        assertThat(token).hasSize(22);
        mvc.perform(as(post("/api/organizations/me/roster/" + carla + "/invite"), "sub-profe", "Profe"))
           .andExpect(jsonPath("$.token").value(token));
        long profe = players.findByExternalSubject("sub-profe").orElseThrow().getId();
        mvc.perform(as(post("/api/organizations/me/roster/" + profe + "/invite"), "sub-profe", "Profe")).andExpect(status().isForbidden());

        // Carla entra con su propia cuenta (otro email): ve de qué perfil se trata y lo reclama
        mvc.perform(as(get("/api/users/claim-invite/" + token), "sub-carla", "Carla"))
           .andExpect(jsonPath("$.firstName").value("Carla")).andExpect(jsonPath("$.organizationName").value("Colegio Andino"));
        mvc.perform(as(post("/api/users/me/claim-invite"), "sub-carla", "Carla").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.ratings.platformRapid").value(1620));
        long me = players.findByExternalSubject("sub-carla").orElseThrow().getId();
        verify(events).publish(eq(UsersEvents.PLAYER_MERGED), eq(Map.of("fromPlayerId", carla, "intoPlayerId", me)));
        assertThat(players.findById(carla).orElseThrow().isActive()).isFalse();

        // La invitación se usa una sola vez; una vencida no sirve
        mvc.perform(as(get("/api/users/claim-invite/" + token), "sub-otro", "Otro")).andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error").value("INVITE_NOT_FOUND"));
        String brunoToken = body(mvc.perform(as(post("/api/organizations/me/roster/" + bruno + "/invite"), "sub-profe", "Profe")))
                .get("token").asText();
        Player b = players.findById(bruno).orElseThrow();
        b.setClaimTokenCreatedAt(Instant.now().minus(Duration.ofDays(31)));
        players.save(b);
        mvc.perform(as(get("/api/users/claim-invite/" + brunoToken), "sub-otro", "Otro")).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("INVITE_EXPIRED"));
        // Pedirla de nuevo genera otra (la vencida se reemplaza)
        mvc.perform(as(post("/api/organizations/me/roster/" + bruno + "/invite"), "sub-profe", "Profe"))
           .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.not(brunoToken)));
    }
}
