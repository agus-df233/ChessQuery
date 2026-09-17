package cl.chessquery.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPlayerIdentityResolverTest {

    private HttpServer server;
    private final AtomicInteger gets = new AtomicInteger();
    private final AtomicInteger posts = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/players/by-subject/", ex -> {
            gets.incrementAndGet();
            String sub = ex.getRequestURI().getPath().substring("/internal/players/by-subject/".length());
            if (sub.equals("known")) {
                respond(ex, 200, "{\"playerId\":5,\"organizationId\":null}");
            } else {
                respond(ex, 404, "{}");
            }
        });
        server.createContext("/internal/players/provision", ex -> {
            posts.incrementAndGet();
            respond(ex, 201, "{\"playerId\":99,\"organizationId\":null}");
        });
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpPlayerIdentityResolver resolver() {
        return new HttpPlayerIdentityResolver(RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
    }

    @Test
    void resolvesAndCaches() {
        HttpPlayerIdentityResolver r = resolver();
        assertThat(r.resolve("known", Map.of()).playerId()).isEqualTo(5L);
        assertThat(r.resolve("known", Map.of()).playerId()).isEqualTo(5L);
        assertThat(gets.get()).isEqualTo(1);
        r.evict("known");
        r.resolve("known", Map.of());
        assertThat(gets.get()).isEqualTo(2);
    }

    @Test
    void provisionsOnNotFound() {
        HttpPlayerIdentityResolver r = resolver();
        var id = r.resolve("new-sub", Map.of("email", "n@x.cl", "given_name", "Ana", "family_name", "Soto"));
        assertThat(id.playerId()).isEqualTo(99L);
        assertThat(posts.get()).isEqualTo(1);
    }
}
