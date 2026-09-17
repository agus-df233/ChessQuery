package cl.chessquery.users.rating;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Stub HTTP local que imita las respuestas públicas de Lichess y Chess.com. */
class ExternalRatingsClientTest {

    private HttpServer server;
    private ExternalRatingsClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/user/ok", ex -> respond(ex, 200,
                "{\"perfs\":{\"bullet\":{\"rating\":1400},\"blitz\":{\"rating\":1500},\"rapid\":{\"rating\":\"x\"},\"classical\":{\"rating\":0}}}"));
        server.createContext("/api/user/missing", ex -> respond(ex, 404, "{}"));
        server.createContext("/pub/player/ok/stats", ex -> respond(ex, 200,
                "{\"chess_bullet\":{\"last\":{\"rating\":900}},\"chess_daily\":{\"last\":{\"rating\":1200}}}"));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new ExternalRatingsClient(base, base);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void lichessParsesOnlyPositiveIntegerRatings() {
        var r = client.lichess(" ok ").orElseThrow();
        assertThat(r).containsEntry(RatingType.LICHESS_BULLET, 1400).containsEntry(RatingType.LICHESS_BLITZ, 1500)
                     .doesNotContainKeys(RatingType.LICHESS_RAPID, RatingType.LICHESS_CLASSICAL);
    }

    @Test
    void chesscomLowercasesUsernameAndParses() {
        var r = client.chesscom("OK").orElseThrow();
        assertThat(r).containsEntry(RatingType.CHESSCOM_BULLET, 900).containsEntry(RatingType.CHESSCOM_DAILY, 1200);
    }

    @Test
    void nonOkAndNetworkErrorsAreEmpty() {
        assertThat(client.lichess("missing")).isEmpty();
        ExternalRatingsClient dead = new ExternalRatingsClient("http://127.0.0.1:1", "http://127.0.0.1:1");
        assertThat(dead.chesscom("x")).isEmpty();
    }
}
