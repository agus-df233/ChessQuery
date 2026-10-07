package cl.chessquery.users;

import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.player.PlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Regresión: al entrar por primera vez la web pide varias cosas a la vez y cada request intenta crear el jugador. Uno
 * gana y los demás deben releer esa fila (antes, el choque con el índice único dejaba la sesión de Hibernate rota y el
 * request respondía 500).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FirstLoginRaceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlayerRepository players;

    @Test
    void variosRequestsSimultaneosDeUnUsuarioNuevoCreanUnSoloJugador() throws Exception {
        int parallel = 8;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < parallel; i++) {
            calls.add(pool.submit(() -> {
                start.await();
                return mvc.perform(get("/api/users/me").with(jwt().jwt(j -> j.subject("sub-nueva")
                        .claim("given_name", "Nueva").claim("family_name", "Jugadora")))).andReturn();
            }));
        }
        start.countDown();
        Set<Long> ids = new HashSet<>();
        for (Future<MvcResult> call : calls) {
            MvcResult r = call.get();
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            ids.add(json.readTree(r.getResponse().getContentAsString()).at("/profile/id").asLong());
        }
        pool.shutdown();
        assertThat(ids).hasSize(1);
        assertThat(players.findByExternalSubject("sub-nueva")).isPresent();
    }
}
