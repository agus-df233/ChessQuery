package cl.chessquery.tournament;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.tournament.events.FederationTournamentConsumer;
import cl.chessquery.tournament.events.PlayerMergedConsumer;
import cl.chessquery.tournament.events.TournamentEvents;
import cl.chessquery.common.rating.PlatformRatings;
import cl.chessquery.tournament.users.UsersClient;
import cl.chessquery.tournament.users.UsersClient.PlayerSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorrido completo del organizador contra PostgreSQL real: crear torneo suizo, inscribir, rondas con resultados,
 * tabla, cierre con rating, TRF y vista pública. users y el bus se simulan (contratos en docs/events.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TournamentFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;
    @MockitoBean UsersClient users;
    @MockitoBean PlayerIdentityResolver identity;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FederationTournamentConsumer federationConsumer;
    @Autowired PlayerMergedConsumer mergedConsumer;

    static final long ORGANIZER = 1;
    static final long ORG_ID = 10;
    static long tournamentId;

    @BeforeEach
    void mocks() {
        when(identity.resolve(anyString(), anyMap())).thenAnswer(inv -> {
            long id = Long.parseLong(inv.getArgument(0, String.class).substring(4));
            return new ResolvedIdentity(id, id == ORGANIZER ? ORG_ID : null);
        });
        when(users.planOf(anyLong())).thenAnswer(inv -> inv.getArgument(0, Long.class) == ORGANIZER
                ? new UsersClient.PlanInfo(ORG_ID, "FREE", 3) : new UsersClient.PlanInfo(null, "FREE", 3));
        when(users.player(anyLong())).thenAnswer(inv -> summary(inv.getArgument(0, Long.class)));
        when(users.players(any())).thenAnswer(inv -> ((Collection<Long>) inv.getArgument(0)).stream()
                .map(TournamentFlowIntegrationTest::summary).toList());
    }

    /**
     * Jugador ficticio: id 11..17, ELO ChessQuery clásico 2100 − 50·(id − 11) (el torneo es 60+30, clásico); el 12 no
     * tiene ELO clásico pero sí relámpago, que no debe usarse; el 17 es menor (apellido abreviado).
     */
    static PlayerSummary summary(long id) {
        Integer classical = id == 12 ? null : 2100 - 50 * (int) (id - 11);
        PlatformRatings platform = new PlatformRatings(null, id == 12 ? 2500 : null, null, classical);
        return new PlayerSummary(id, "Jugador" + id, "Apellido" + id, id == 17 ? "A." : "Apellido" + id,
                id == 11 ? "FM" : null, "Club Torre", 1600, null, platform, id == 11 ? "3400011" : null, null,
                id == 17 ? 2014 : 1990, "M", false, null, true);
    }

    static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, long playerId) {
        return b.with(jwt().jwt(j -> j.subject("sub-" + playerId)));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test @Order(1)
    void organizadorCreaElTorneoYUnJugadorNoPuede() throws Exception {
        String req = """
                {"name":"Abierto de Primavera","city":"Santiago","region":"RM","startDate":"2026-10-03",
                 "format":"SWISS","rounds":3,"timeControl":"60+30","rated":true}""";
        mvc.perform(as(post("/api/tournaments"), 11).contentType(MediaType.APPLICATION_JSON).content(req))
           .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("NOT_ORGANIZER"));
        JsonNode created = body(mvc.perform(as(post("/api/tournaments"), ORGANIZER).contentType(MediaType.APPLICATION_JSON).content(req))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.tournament.status").value("OPEN"))
           .andExpect(jsonPath("$.tournament.organizationId").value(ORG_ID))
           .andExpect(jsonPath("$.tournament.baseMinutes").value(60)) // leído de la etiqueta "60+30"
           .andExpect(jsonPath("$.tournament.incrementSeconds").value(30))
           .andExpect(jsonPath("$.tournament.category").value("CLASSICAL")));
        tournamentId = created.at("/tournament/id").asLong();
        // Con los campos estructurados y sin etiqueta: la etiqueta se genera y el ritmo cambia (3+2 = relámpago)
        String blitz = req.replace("\"timeControl\":\"60+30\"", "\"baseMinutes\":3,\"incrementSeconds\":2");
        mvc.perform(as(put("/api/tournaments/" + tournamentId), ORGANIZER).contentType(MediaType.APPLICATION_JSON).content(blitz))
           .andExpect(jsonPath("$.tournament.timeControl").value("3+2"))
           .andExpect(jsonPath("$.tournament.category").value("BLITZ"));
        mvc.perform(as(put("/api/tournaments/" + tournamentId), ORGANIZER).contentType(MediaType.APPLICATION_JSON)
                .content(blitz.replace("\"baseMinutes\":3", "\"baseMinutes\":301")))
           .andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/tournaments/" + tournamentId), ORGANIZER).contentType(MediaType.APPLICATION_JSON).content(req))
           .andExpect(jsonPath("$.tournament.category").value("CLASSICAL"));
        mvc.perform(as(post("/api/tournaments"), ORGANIZER).contentType(MediaType.APPLICATION_JSON)
                .content(req.replace("\"startDate\":\"2026-10-03\"", "\"startDate\":\"2026-10-03\",\"endDate\":\"2026-10-01\"")))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_DATES"));
    }

    @Test @Order(2)
    void inscripcionesDelOrganizadorYDelJugador() throws Exception {
        for (long id = 11; id <= 16; id++) {
            mvc.perform(as(post("/api/tournaments/" + tournamentId + "/registrations"), ORGANIZER)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\":" + id + "}")).andExpect(status().isOk());
        }
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/registrations"), ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\":11}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_REGISTERED"));
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/registrations"), 13)
                .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\":14}"))
           .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/join"), 17)).andExpect(status().isOk());
        mvc.perform(as(delete("/api/tournaments/" + tournamentId + "/join"), 17))
           .andExpect(jsonPath("$.players.length()").value(6));
        mvc.perform(as(delete("/api/tournaments/" + tournamentId + "/registrations/16"), 15)).andExpect(status().isForbidden());
        mvc.perform(as(put("/api/tournaments/" + tournamentId), ORGANIZER).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Abierto de Primavera 2026","city":"Santiago","startDate":"2026-10-03","format":"SWISS","rounds":3,
                 "baseMinutes":60,"incrementSeconds":30}"""))
           .andExpect(jsonPath("$.tournament.name").value("Abierto de Primavera 2026"))
           .andExpect(jsonPath("$.tournament.rated").value(true));
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/join"), 17))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.players.length()").value(7))
           .andExpect(jsonPath("$.players[0].player.name").value("Jugador11 Apellido11"))
           .andExpect(jsonPath("$.players[0].player.title").value("FM"));
        // El menor aparece con el apellido abreviado en la vista pública
        mvc.perform(get("/api/public/tournaments/" + tournamentId))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.players[?(@.player.playerId == 17)].player.name").value("Jugador17 A."));
        mvc.perform(as(get("/api/tournaments/mine"), 17)).andExpect(jsonPath("$.registered[0].id").value(tournamentId));
        mvc.perform(as(get("/api/tournaments/mine"), ORGANIZER)).andExpect(jsonPath("$.organized[0].id").value(tournamentId));
    }

    @Test @Order(3)
    void tresRondasSuizasConResultadosYReglas() throws Exception {
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/rounds"), 11)).andExpect(status().isForbidden());
        for (int round = 1; round <= 3; round++) {
            JsonNode r = body(mvc.perform(as(post("/api/tournaments/" + tournamentId + "/rounds"), ORGANIZER))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.number").value(round))
                    .andExpect(jsonPath("$.boards.length()").value(4))
                    .andExpect(jsonPath("$.boards[3].black").doesNotExist())
                    .andExpect(jsonPath("$.boards[3].result").value("BYE")));
            if (round == 1) {
                assertThat(r.at("/boards/0/white/playerId").asLong()).isEqualTo(11); // 1º siembra con blancas
                mvc.perform(as(post("/api/tournaments/" + tournamentId + "/rounds"), ORGANIZER))
                   .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ROUND_INCOMPLETE"));
                mvc.perform(as(post("/api/tournaments/" + tournamentId + "/join"), 18))
                   .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REGISTRATION_CLOSED"));
            }
            for (int board = 1; board <= 3; board++) {
                String result = board == 2 ? "DRAW" : "WHITE_WINS";
                mvc.perform(as(put("/api/tournaments/" + tournamentId + "/rounds/" + round + "/boards/" + board), ORGANIZER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"" + result + "\"}"))
                   .andExpect(status().isOk());
            }
            mvc.perform(as(put("/api/tournaments/" + tournamentId + "/rounds/" + round + "/boards/4"), ORGANIZER)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"DRAW\"}"))
               .andExpect(status().isBadRequest());
        }
        verify(events, atLeastOnce()).publish(eq(TournamentEvents.ROUND_GENERATED), any());
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/rounds"), ORGANIZER))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALL_ROUNDS_PLAYED"));
        mvc.perform(as(put("/api/tournaments/" + tournamentId + "/rounds/1/boards/1"), ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"DRAW\"}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ROUND_CLOSED"));

        // Nadie repite rival y nadie recibe dos byes
        JsonNode rounds = body(mvc.perform(get("/api/public/tournaments/" + tournamentId + "/rounds")).andExpect(status().isOk()));
        java.util.Set<String> games = new java.util.HashSet<>();
        java.util.Set<Long> byes = new java.util.HashSet<>();
        for (JsonNode round : rounds) {
            for (JsonNode b : round.get("boards")) {
                long w = b.at("/white/playerId").asLong();
                if (b.get("black") == null || b.get("black").isNull()) { assertThat(byes.add(w)).isTrue(); continue; }
                long bl = b.at("/black/playerId").asLong();
                assertThat(games.add(Math.min(w, bl) + "-" + Math.max(w, bl))).isTrue();
            }
        }
    }

    @Test @Order(4)
    void tablaCierreConRatingTrfYVistaPublica() throws Exception {
        mvc.perform(get("/api/public/tournaments/" + tournamentId + "/standings"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(7))
           .andExpect(jsonPath("$[0].position").value(1))
           .andExpect(jsonPath("$[0].buchholzCut1").isNumber());
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/finish"), 12)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/finish"), ORGANIZER))
           .andExpect(status().isOk()).andExpect(jsonPath("$[0].points").isNumber());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloads = ArgumentCaptor.forClass(Map.class);
        verify(events, atLeastOnce()).publish(eq(TournamentEvents.ELO_UPDATED), payloads.capture());
        List<Map<String, Object>> elo = payloads.getAllValues();
        assertThat(elo).hasSize(7).allSatisfy(p -> {
            assertThat(p).containsEntry("ratingType", "PLATFORM_CLASSICAL").containsEntry("source", "TOURNAMENT");
            assertThat((int) p.get("newElo") - (int) p.get("oldElo")).isEqualTo(p.get("delta"));
        });
        // El 12 no tenía ELO clásico (su 2500 es de relámpago y no cuenta): parte de su nacional (1600) con K = 40
        assertThat(elo.stream().filter(p -> p.get("playerId").equals(12L)).findFirst().orElseThrow())
                .containsEntry("oldElo", 1600);
        verify(events).publish(eq(TournamentEvents.FINISHED), any());

        mvc.perform(get("/api/public/tournaments").param("status", "FINISHED"))
           .andExpect(jsonPath("$[0].status").value("FINISHED")).andExpect(jsonPath("$[0].currentRound").value(3));
        String trf = mvc.perform(as(get("/api/tournaments/" + tournamentId + "/trf"), ORGANIZER))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
           .andReturn().getResponse().getContentAsString();
        assertThat(trf).startsWith("012 Abierto de Primavera 2026\n");
        assertThat(trf.lines().filter(l -> l.startsWith("001"))).hasSize(7);
        assertThat(trf).contains("Apellido17, Jugador17"); // el organizador exporta el nombre completo
        assertThat(trf).contains("3400011");
        mvc.perform(as(get("/api/tournaments/" + tournamentId + "/trf"), 11)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/tournaments/" + tournamentId + "/finish"), ORGANIZER))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NOT_IN_PROGRESS"));
    }

    @Test @Order(5)
    void roundRobinDeCuatroSeCompletaEnTresRondas() throws Exception {
        String req = """
                {"name":"Cuadrangular","startDate":"2026-11-01","format":"ROUND_ROBIN","rounds":1,"rated":false}""";
        long id = body(mvc.perform(as(post("/api/tournaments"), ORGANIZER).contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated())).at("/tournament/id").asLong();
        for (long p = 11; p <= 14; p++) {
            mvc.perform(as(post("/api/tournaments/" + id + "/registrations"), ORGANIZER)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\":" + p + "}")).andExpect(status().isOk());
        }
        for (int round = 1; round <= 3; round++) {
            mvc.perform(as(post("/api/tournaments/" + id + "/rounds"), ORGANIZER)).andExpect(status().isCreated());
            for (int board = 1; board <= 2; board++) {
                mvc.perform(as(put("/api/tournaments/" + id + "/rounds/" + round + "/boards/" + board), ORGANIZER)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"BLACK_WINS\"}")).andExpect(status().isOk());
            }
        }
        mvc.perform(get("/api/public/tournaments/" + id)).andExpect(jsonPath("$.tournament.roundsPlanned").value(3));
        mvc.perform(as(post("/api/tournaments/" + id + "/rounds"), ORGANIZER))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALL_ROUNDS_PLAYED"));
        mvc.perform(as(post("/api/tournaments/" + id + "/finish"), ORGANIZER)).andExpect(status().isOk());
    }

    @Test @Order(6)
    void calendarioDeLaFederacionYFusionDeJugadores() throws Exception {
        federationConsumer.apply(ChessEvent.of(TournamentEvents.FEDERATION_TOURNAMENT_PUBLISHED, Map.of("tournaments", List.of(
                Map.of("federationTournamentId", "901", "title", "Nacional Juvenil", "city", "Temuco",
                        "startDate", java.time.LocalDate.now().plusDays(10).toString(), "ratedFide", true),
                Map.of("federationTournamentId", "902", "title", "Sin fecha")))));
        mvc.perform(get("/api/public/tournaments/calendar"))
           .andExpect(jsonPath("$.length()").value(1))
           .andExpect(jsonPath("$[0].title").value("Nacional Juvenil"))
           .andExpect(jsonPath("$[0].ratedFide").value(true));

        mergedConsumer.apply(ChessEvent.of(TournamentEvents.PLAYER_MERGED, Map.of("fromPlayerId", 17, "intoPlayerId", 99)));
        mvc.perform(as(get("/api/tournaments/mine"), 99)).andExpect(jsonPath("$.registered[0].id").value(tournamentId));
        String rounds = mvc.perform(get("/api/public/tournaments/" + tournamentId + "/rounds")).andReturn().getResponse().getContentAsString();
        assertThat(rounds).contains("\"playerId\":99").doesNotContain("\"playerId\":17,");
    }
}
