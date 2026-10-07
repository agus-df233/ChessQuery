package cl.chessquery.game;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.game.events.GameEvents;
import cl.chessquery.game.users.UsersClient;
import cl.chessquery.game.users.UsersClient.PlayerSummary;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sala de juego de punta a punta contra PostgreSQL real: un profesor abre una sala de 2 tableros, entran jugadores con
 * el código (uno queda de espectador), los asigna, inicia, juegan con los endpoints de siempre, revancha, sube a 3
 * tableros y cierra. Las partidas nunca publican ELO. También los abusos: no organizador, otro organizador, alguien
 * que no entró, un espectador que intenta jugar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RoomFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;
    @MockitoBean UsersClient users;
    @MockitoBean PlayerIdentityResolver identity;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    /** Profesor del club 7, otro organizador (club 8) y los alumnos 1 a 6. */
    static final long PROFE = 100, OTRO_PROFE = 101;

    @BeforeEach
    void mocks() {
        when(identity.resolve(anyString(), anyMap())).thenAnswer(inv -> {
            long id = Long.parseLong(inv.getArgument(0, String.class).substring(4));
            Long organization = id == PROFE ? Long.valueOf(7) : id == OTRO_PROFE ? Long.valueOf(8) : null;
            return new ResolvedIdentity(id, organization);
        });
        when(users.player(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0, Long.class);
            return new PlayerSummary(id, "Alumno" + id, id == 6 ? "M." : "Apellido", null, null, 1200, null, true);
        });
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

    private ResultActions assign(long room, int board, Long white, Long black) throws Exception {
        return send(put("/api/rooms/" + room + "/boards/" + board), PROFE,
                "{\"whitePlayerId\":" + white + ",\"blackPlayerId\":" + black + "}");
    }

    private long version(long room) throws Exception {
        return body(mvc.perform(as(get("/api/rooms/" + room), PROFE))).get("version").asLong();
    }

    /** La sala se entera de las jugadas en otro hilo (RoomLive): espera a que suba su versión. */
    private void awaitVersionAbove(long room, long before) throws Exception {
        for (int i = 0; i < 50 && version(room) <= before; i++) Thread.sleep(100);
        assertThat(version(room)).isGreaterThan(before);
    }

    @Test
    void claseCompletaEnUnaSalaDeJuego() throws Exception {
        String clase = "{\"name\":\"Clase 4°B\",\"boards\":2,\"maxPlayers\":5,\"minutes\":10,\"incrementSeconds\":0}";
        send(post("/api/rooms"), 1, clase).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("NOT_ORGANIZER"));
        JsonNode created = body(send(post("/api/rooms"), PROFE, clase)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizer").value(true))
                .andExpect(jsonPath("$.boardCount").value(2))
                .andExpect(jsonPath("$.boards.length()").value(2))
                .andExpect(jsonPath("$.category").value("RAPID")));
        long room = created.get("id").asLong();
        String code = created.get("code").asText();
        assertThat(code).matches("[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}");

        // Entrar: código inválido (mismo 404 que una sala cerrada), con minúsculas y guion, cupo lleno, sin duplicar
        send(post("/api/rooms/join"), 1, "{\"code\":\"ZZZZZZ\"}").andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error").value("ROOM_NOT_FOUND"));
        String typed = code.substring(0, 3).toLowerCase() + "-" + code.substring(3);
        send(post("/api/rooms/join"), 1, "{\"code\":\"" + typed + "\"}").andExpect(status().isOk())
           .andExpect(jsonPath("$.organizer").value(false)).andExpect(jsonPath("$.myBoard").doesNotExist());
        for (long p = 2; p <= 5; p++) send(post("/api/rooms/join"), p, "{\"code\":\"" + code + "\"}").andExpect(status().isOk());
        send(post("/api/rooms/join"), 6, "{\"code\":\"" + code + "\"}").andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("ROOM_FULL"));
        send(post("/api/rooms/join"), 1, "{\"code\":\"" + code + "\"}").andExpect(jsonPath("$.members.length()").value(5));

        // Solo el organizador y los miembros ven la sala; solo su organizador la gestiona
        mvc.perform(as(get("/api/rooms/" + room), 6)).andExpect(status().isForbidden())
           .andExpect(jsonPath("$.error").value("NOT_IN_ROOM"));
        mvc.perform(get("/api/rooms/" + room)).andExpect(status().isUnauthorized());
        send(put("/api/rooms/" + room + "/boards/1"), OTRO_PROFE, "{\"whitePlayerId\":1,\"blackPlayerId\":2}")
           .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("NOT_ROOM_ORGANIZER"));

        // Asignar: 1-2 en el tablero 1, 3-4 en el 2; el 5 queda mirando
        assign(room, 1, 6L, 2L).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("NOT_A_MEMBER"));
        assign(room, 1, 1L, 1L).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("SAME_PLAYER"));
        assign(room, 9, 1L, 2L).andExpect(status().isNotFound());
        assign(room, 1, 1L, 2L).andExpect(status().isOk());
        assign(room, 2, 3L, 4L).andExpect(jsonPath("$.members[4].boardNo").doesNotExist())
           .andExpect(jsonPath("$.members[0].boardNo").value(1)).andExpect(jsonPath("$.members[0].color").value("WHITE"));
        mvc.perform(as(post("/api/rooms/" + room + "/boards/1/start"), 1)).andExpect(status().isForbidden());

        // Iniciar el tablero 1: partida normal, ya en juego, sin rating
        long game = body(mvc.perform(as(post("/api/rooms/" + room + "/boards/1/start"), PROFE))
                .andExpect(jsonPath("$.boards[0].game.status").value("ACTIVE"))
                .andExpect(jsonPath("$.boards[0].game.rated").value(false))
                .andExpect(jsonPath("$.boards[0].game.roomId").value(room))
                .andExpect(jsonPath("$.boards[0].game.boardNo").value(1))
                .andExpect(jsonPath("$.boards[0].game.category").value("RAPID"))).at("/boards/0/game/id").asLong();
        mvc.perform(as(get("/api/rooms/" + room), 1)).andExpect(jsonPath("$.myBoard").value(1))
           .andExpect(jsonPath("$.myGameId").value(game));

        // Con partida en curso el tablero no se toca
        assign(room, 1, 5L, 2L).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("BOARD_IN_PLAY"));
        assign(room, 2, 1L, 4L).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PLAYER_IN_GAME"));
        mvc.perform(as(delete("/api/rooms/" + room + "/members/1"), PROFE)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("PLAYER_IN_GAME"));

        // Se juega con los endpoints de siempre; el espectador no puede mover; la sala se entera de la jugada
        send(post("/api/games/" + game + "/moves"), 5, "{\"uci\":\"e2e4\"}").andExpect(status().isForbidden());
        long before = version(room);
        send(post("/api/games/" + game + "/moves"), 1, "{\"uci\":\"e2e4\"}").andExpect(status().isOk());
        awaitVersionAbove(room, before);
        mvc.perform(as(get("/api/rooms/" + room), 5)).andExpect(jsonPath("$.boards[0].game.moves[0]").value("e2e4"));
        mvc.perform(as(post("/api/games/" + game + "/resign"), 2)).andExpect(jsonPath("$.result").value("WHITE_WINS"));
        verify(events, never()).publish(eq(GameEvents.ELO_UPDATED), any());

        // Revancha con colores invertidos; iniciar todos arranca el tablero 2
        mvc.perform(as(post("/api/rooms/" + room + "/boards/2/rematch"), PROFE)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("NO_FINISHED_GAME"));
        mvc.perform(as(post("/api/rooms/" + room + "/boards/1/rematch"), PROFE))
           .andExpect(jsonPath("$.boards[0].white.playerId").value(2))
           .andExpect(jsonPath("$.boards[0].game.status").value("ACTIVE"));
        mvc.perform(as(post("/api/rooms/" + room + "/start"), PROFE)).andExpect(jsonPath("$.boards[1].game.status").value("ACTIVE"));
        mvc.perform(as(post("/api/rooms/" + room + "/start"), PROFE)).andExpect(status().isConflict())
           .andExpect(jsonPath("$.error").value("NOTHING_TO_START"));

        // Reconfigurar: el cupo no baja de los que ya entraron, ni se quitan tableros en juego; subir a 3 sí
        String tres = "{\"name\":\"Clase 4°B\",\"boards\":3,\"maxPlayers\":6,\"minutes\":5,\"incrementSeconds\":3}";
        send(put("/api/rooms/" + room), PROFE, tres.replace("\"maxPlayers\":6", "\"maxPlayers\":4"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("MAX_BELOW_MEMBERS"));
        send(put("/api/rooms/" + room), PROFE, tres.replace("\"boards\":3", "\"boards\":1"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("BOARD_IN_PLAY"));
        send(put("/api/rooms/" + room), PROFE, tres).andExpect(jsonPath("$.boardCount").value(3))
           .andExpect(jsonPath("$.boards.length()").value(3)).andExpect(jsonPath("$.category").value("BLITZ"));
        send(post("/api/rooms/join"), 6, "{\"code\":\"" + code + "\"}").andExpect(status().isOk());
        assign(room, 3, 5L, 6L).andExpect(jsonPath("$.boards[2].black.name").value("Alumno6 M."));
        mvc.perform(as(post("/api/rooms/" + room + "/boards/3/start"), PROFE))
           .andExpect(jsonPath("$.boards[2].game.initialSeconds").value(300));

        // Mis salas: el organizador ve el código; el alumno no lo necesita en la lista
        mvc.perform(as(get("/api/rooms/mine"), PROFE)).andExpect(jsonPath("$.organized[0].code").value(code))
           .andExpect(jsonPath("$.organized[0].memberCount").value(6));
        mvc.perform(as(get("/api/rooms/mine"), 1)).andExpect(jsonPath("$.joined[0].id").value(room))
           .andExpect(jsonPath("$.joined[0].code").doesNotExist());

        // Long polling de la sala (respaldo del WebSocket): despierta con el cierre
        MvcResult waiting = mvc.perform(as(get("/api/rooms/" + room).param("afterVersion", String.valueOf(version(room))), 1))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(as(post("/api/rooms/" + room + "/close"), PROFE)).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(asyncDispatch(waiting)).andExpect(jsonPath("$.status").value("CLOSED"));

        // Cerrada: no entra nadie ni se inicia nada; las partidas en curso siguen
        send(post("/api/rooms/join"), 1, "{\"code\":\"" + code + "\"}").andExpect(status().isNotFound());
        assign(room, 3, null, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ROOM_CLOSED"));
        mvc.perform(as(get("/api/rooms/" + room), 3)).andExpect(jsonPath("$.boards[1].game.status").value("ACTIVE"));
    }

    @Test
    void limitesDeSalasYDeIntentos() throws Exception {
        String sala = "{\"name\":\"Práctica\",\"boards\":1,\"minutes\":3,\"incrementSeconds\":2}";
        send(post("/api/rooms"), PROFE, sala.replace("\"boards\":1", "\"boards\":17")).andExpect(status().isBadRequest());
        send(post("/api/rooms"), PROFE, sala.replace("\"minutes\":3", "\"minutes\":0")).andExpect(status().isBadRequest());
        JsonNode first = body(send(post("/api/rooms"), OTRO_PROFE, sala).andExpect(jsonPath("$.maxPlayers").value(2)));
        send(post("/api/rooms"), OTRO_PROFE, sala).andExpect(status().isCreated());
        send(post("/api/rooms"), OTRO_PROFE, sala).andExpect(status().isCreated());
        send(post("/api/rooms"), OTRO_PROFE, sala).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ROOM_LIMIT"));

        // Un jugador que se va de la sala (no está jugando) deja de verla
        long room = first.get("id").asLong();
        send(post("/api/rooms/join"), 3, "{\"code\":\"" + first.get("code").asText() + "\"}").andExpect(status().isOk());
        mvc.perform(as(delete("/api/rooms/" + room + "/members/3"), 3)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/rooms/" + room), 3)).andExpect(status().isForbidden());

        // Probar códigos al azar: tras 10 intentos por minuto, 429
        for (int i = 0; i < 9; i++) send(post("/api/rooms/join"), 4, "{\"code\":\"X\"}").andExpect(status().isNotFound());
        send(post("/api/rooms/join"), 4, "{\"code\":\"ABCDEF\"}").andExpect(status().isTooManyRequests())
           .andExpect(jsonPath("$.error").value("TOO_MANY_ATTEMPTS"));
    }
}
