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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inscripción completa contra PostgreSQL real: reglas del torneo (rango de rating, aprobación, cupo y lista de espera
 * que avanza sola, fecha de cierre), inscripción en bloque, acreditación con QR o a mano, no presentados en la ronda 1
 * y retiros durante el torneo (suizo y todos contra todos).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RegistrationFlowIntegrationTest {

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
    /** Rating rápido de cada jugador ficticio (el 15 queda bajo el mínimo del torneo). */
    static final Map<Long, Integer> RAPID = Map.of(11L, 2000, 12L, 1900, 13L, 1800, 14L, 1700, 15L, 1400, 16L, 1600);

    @BeforeEach
    void mocks() {
        when(identity.resolve(anyString(), anyMap())).thenAnswer(inv -> {
            long id = Long.parseLong(inv.getArgument(0, String.class).substring(4));
            return new ResolvedIdentity(id, id == ORGANIZER ? Long.valueOf(ORG_ID) : null);
        });
        when(users.planOf(anyLong())).thenAnswer(inv -> inv.getArgument(0, Long.class) == ORGANIZER
                ? new UsersClient.PlanInfo(ORG_ID, "FREE", 3) : new UsersClient.PlanInfo(null, "FREE", 3));
        when(users.player(anyLong())).thenAnswer(inv -> summary(inv.getArgument(0, Long.class)));
        when(users.players(any())).thenAnswer(inv -> ((Collection<Long>) inv.getArgument(0)).stream()
                .filter(RAPID::containsKey).map(RegistrationFlowIntegrationTest::summary).toList());
    }

    static PlayerSummary summary(long id) {
        return new PlayerSummary(id, "Jugador" + id, "Apellido" + id, "Apellido" + id, null, "Club", null, null,
                new PlatformRatings(null, null, RAPID.getOrDefault(id, 1500), null), null, null, 2000, "M", false, null, true);
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long playerId) {
        return b.with(jwt().jwt(j -> j.subject("sub-" + playerId)));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpServletRequestBuilder b, long player, String content) throws Exception {
        return mvc.perform(as(b, player).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private long create(String extra, String format, int rounds) throws Exception {
        String req = "{\"name\":\"Torneo\",\"startDate\":\"2026-11-01\",\"format\":\"" + format + "\",\"rounds\":" + rounds
                + ",\"baseMinutes\":15,\"incrementSeconds\":10" + extra + "}";
        return body(send(post("/api/tournaments"), ORGANIZER, req).andExpect(status().isCreated())).at("/tournament/id").asLong();
    }

    private Map<Long, String> codes(long id) throws Exception {
        Map<Long, String> codes = new HashMap<>();
        body(mvc.perform(as(get("/api/tournaments/" + id + "/registrations"), ORGANIZER)))
                .forEach(r -> codes.put(r.get("playerId").asLong(), r.get("checkinCode").asText()));
        return codes;
    }

    @Test
    void inscripcionConReglasAcreditacionYNoPresentados() throws Exception {
        String closes = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        send(post("/api/tournaments"), ORGANIZER, "{\"name\":\"X\",\"startDate\":\"2026-11-01\",\"format\":\"SWISS\",\"rounds\":2,"
                + "\"minRating\":2000,\"maxRating\":1500}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_RATING_RANGE"));
        long id = create(",\"maxPlayers\":3,\"requiresApproval\":true,\"minRating\":1500,\"maxRating\":2100,"
                + "\"checkinRequired\":true,\"registrationClosesAt\":\"" + closes + "\"", "SWISS", 2);

        // Reglas: rango de rating; con aprobación quedan pendientes; con el cupo (3) tomado, lista de espera
        mvc.perform(as(post("/api/tournaments/" + id + "/join"), 15)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("RATING_OUT_OF_RANGE"));
        for (long p : new long[] {11, 12, 13, 14}) mvc.perform(as(post("/api/tournaments/" + id + "/join"), p)).andExpect(status().isOk());
        mvc.perform(get("/api/public/tournaments/" + id)).andExpect(jsonPath("$.tournament.pendingCount").value(3))
           .andExpect(jsonPath("$.tournament.waitlistCount").value(1)).andExpect(jsonPath("$.tournament.playerCount").value(0))
           .andExpect(jsonPath("$.players.length()").value(0)); // pendientes y espera no aparecen en público
        mvc.perform(as(get("/api/tournaments/" + id + "/my-registration"), 14)).andExpect(jsonPath("$.status").value("WAITLIST"));
        mvc.perform(as(get("/api/tournaments/" + id + "/registrations"), 11)).andExpect(status().isForbidden());

        // Aprobar; la espera no entra con el cupo lleno; al irse uno, la espera avanza (a pendiente: hay aprobación)
        for (long p : new long[] {11, 12}) mvc.perform(as(post("/api/tournaments/" + id + "/registrations/" + p + "/approve"), ORGANIZER)).andExpect(status().isOk());
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations/14/approve"), ORGANIZER)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("TOURNAMENT_FULL"));
        mvc.perform(as(delete("/api/tournaments/" + id + "/join"), 13)).andExpect(status().isOk());
        mvc.perform(as(get("/api/tournaments/" + id + "/my-registration"), 14)).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations/14/approve"), ORGANIZER))
           .andExpect(jsonPath("$.players.length()").value(3));
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations/14/approve"), ORGANIZER)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("NOT_PENDING"));

        // Acreditación: QR (repetirlo avisa), código ajeno, a mano y deshacer
        Map<Long, String> codes = codes(id);
        assertThat(codes.get(11L)).hasSize(22);
        send(post("/api/tournaments/" + id + "/checkin"), 11, "{\"code\":\"" + codes.get(11L) + "\"}").andExpect(status().isForbidden());
        send(post("/api/tournaments/" + id + "/checkin"), ORGANIZER, "{\"code\":\"" + codes.get(11L) + "\"}")
           .andExpect(jsonPath("$.alreadyCheckedIn").value(false)).andExpect(jsonPath("$.registration.checkedInAt").exists());
        send(post("/api/tournaments/" + id + "/checkin"), ORGANIZER, "{\"code\":\"" + codes.get(11L) + "\"}")
           .andExpect(jsonPath("$.alreadyCheckedIn").value(true));
        send(post("/api/tournaments/" + id + "/checkin"), ORGANIZER, "{\"code\":\"no-existe\"}").andExpect(status().isNotFound());
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations/14/checkin"), ORGANIZER)).andExpect(status().isOk());
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations/12/checkin"), ORGANIZER)).andExpect(status().isOk());
        mvc.perform(as(delete("/api/tournaments/" + id + "/registrations/12/checkin"), ORGANIZER))
           .andExpect(jsonPath("$.checkedInAt").doesNotExist());

        // Ronda 1: el 12 no se acreditó → no se presentó y no juega; mesa única 11 vs 14
        mvc.perform(as(post("/api/tournaments/" + id + "/rounds"), ORGANIZER)).andExpect(status().isCreated())
           .andExpect(jsonPath("$.boards.length()").value(1));
        mvc.perform(as(get("/api/tournaments/" + id + "/my-registration"), 12)).andExpect(jsonPath("$.status").value("WITHDRAWN"))
           .andExpect(jsonPath("$.withdrawnFromRound").value(1));
        mvc.perform(get("/api/public/tournaments/" + id)).andExpect(jsonPath("$.players.length()").value(2));
        send(post("/api/tournaments/" + id + "/checkin"), ORGANIZER, "{\"code\":\"" + codes.get(14L) + "\"}")
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REGISTRATION_CLOSED"));

        // Retiro durante el torneo: el 14 deja de emparejarse; con uno solo en juego no hay ronda 2
        send(put("/api/tournaments/" + id + "/rounds/1/boards/1"), ORGANIZER, "{\"result\":\"DRAW\"}").andExpect(status().isOk());
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations/14/withdraw"), ORGANIZER))
           .andExpect(jsonPath("$.players[1].withdrawnFromRound").value(2));
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations/12/withdraw"), ORGANIZER)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("NOT_PLAYING"));
        mvc.perform(as(post("/api/tournaments/" + id + "/rounds"), ORGANIZER)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("NOT_ENOUGH_PLAYERS"));
        // Quien jugó y se retiró sigue en la tabla
        mvc.perform(get("/api/public/tournaments/" + id + "/standings")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void cierreInscripcionEnBloqueYRetiroEnTodosContraTodos() throws Exception {
        String closed = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        long id = create(",\"registrationClosesAt\":\"" + closed + "\"", "ROUND_ROBIN", 1);
        mvc.perform(as(post("/api/tournaments/" + id + "/join"), 16)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("REGISTRATION_DEADLINE_PASSED"));

        // El organizador inscribe en bloque (el cierre es para la inscripción de los jugadores, no para él)
        JsonNode report = body(send(post("/api/tournaments/" + id + "/registrations/bulk"), ORGANIZER,
                "{\"playerIds\":[11,12,13,14,999,11]}").andExpect(status().isOk()));
        assertThat(report).hasSize(5);
        assertThat(report.get(0).get("outcome").asText()).isEqualTo("REGISTERED");
        assertThat(report.get(4).get("outcome").asText()).isEqualTo("ERROR");
        send(post("/api/tournaments/" + id + "/registrations/bulk"), ORGANIZER, "{\"playerIds\":[11]}")
           .andExpect(jsonPath("$[0].outcome").value("ALREADY_REGISTERED"));
        send(post("/api/tournaments/" + id + "/registrations/bulk"), 11, "{\"playerIds\":[12]}").andExpect(status().isForbidden());
        send(post("/api/tournaments/" + id + "/registrations/bulk"), ORGANIZER, "{\"playerIds\":[]}").andExpect(status().isBadRequest());

        // Todos contra todos de 4: 3 rondas. El 14 se retira tras la ronda 1: sus partidas quedan como no presentación
        mvc.perform(as(post("/api/tournaments/" + id + "/rounds"), ORGANIZER)).andExpect(status().isCreated());
        for (int board = 1; board <= 2; board++) {
            send(put("/api/tournaments/" + id + "/rounds/1/boards/" + board), ORGANIZER, "{\"result\":\"WHITE_WINS\"}").andExpect(status().isOk());
        }
        mvc.perform(as(post("/api/tournaments/" + id + "/registrations/14/withdraw"), ORGANIZER)).andExpect(status().isOk());
        JsonNode round2 = body(mvc.perform(as(post("/api/tournaments/" + id + "/rounds"), ORGANIZER)).andExpect(status().isCreated()));
        long forfeits = 0;
        for (JsonNode b : round2.get("boards")) {
            boolean withdrawn = b.at("/white/playerId").asLong() == 14 || b.at("/black/playerId").asLong() == 14;
            if (withdrawn) {
                forfeits++;
                assertThat(b.get("resultLabel").asText()).endsWith("NP");
            } else {
                assertThat(b.get("result").isNull()).isTrue();
            }
        }
        assertThat(forfeits).isEqualTo(1);
    }
}
