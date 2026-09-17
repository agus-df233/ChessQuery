package cl.chessquery.users.rating;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente de las APIs públicas (sin token) de Lichess y Chess.com para leer los ratings
 * por modalidad de un usuario. Best-effort: cualquier fallo devuelve vacío y no rompe el
 * flujo del jugador. Un solo HttpClient para ambas fuentes.
 */
@Slf4j
@Component
public class ExternalRatingsClient {

    private final String lichessBase;
    private final String chesscomBase;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    public ExternalRatingsClient(@Value("${chessquery.external-ratings.lichess-base-url}") String lichessBase,
                                 @Value("${chessquery.external-ratings.chesscom-base-url}") String chesscomBase) {
        this.lichessBase = lichessBase;
        this.chesscomBase = chesscomBase;
    }

    /** Lichess: {@code GET /api/user/{username}} → {@code perfs.<modo>.rating}. */
    public Optional<Map<RatingType, Integer>> lichess(String username) {
        return fetch(lichessBase + "/api/user/" + username.trim()).map(body -> {
            JsonNode perfs = body.path("perfs");
            Map<RatingType, Integer> out = new EnumMap<>(RatingType.class);
            put(out, RatingType.LICHESS_BULLET, perfs.path("bullet").path("rating"));
            put(out, RatingType.LICHESS_BLITZ, perfs.path("blitz").path("rating"));
            put(out, RatingType.LICHESS_RAPID, perfs.path("rapid").path("rating"));
            put(out, RatingType.LICHESS_CLASSICAL, perfs.path("classical").path("rating"));
            return out;
        });
    }

    /** Chess.com: {@code GET /pub/player/{username}/stats} → {@code chess_<modo>.last.rating}. */
    public Optional<Map<RatingType, Integer>> chesscom(String username) {
        return fetch(chesscomBase + "/pub/player/" + username.trim().toLowerCase() + "/stats").map(body -> {
            Map<RatingType, Integer> out = new EnumMap<>(RatingType.class);
            put(out, RatingType.CHESSCOM_BULLET, body.path("chess_bullet").path("last").path("rating"));
            put(out, RatingType.CHESSCOM_BLITZ, body.path("chess_blitz").path("last").path("rating"));
            put(out, RatingType.CHESSCOM_RAPID, body.path("chess_rapid").path("last").path("rating"));
            put(out, RatingType.CHESSCOM_DAILY, body.path("chess_daily").path("last").path("rating"));
            return out;
        });
    }

    private Optional<JsonNode> fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "ChessQuery/3 (contacto@chessquery.cl)")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("{} → HTTP {}", url, resp.statusCode());
                return Optional.empty();
            }
            return Optional.of(json.readTree(resp.body()));
        } catch (Exception e) {
            log.warn("Fallo leyendo {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private static void put(Map<RatingType, Integer> out, RatingType type, JsonNode node) {
        if (node.isInt() && node.asInt() > 0) out.put(type, node.asInt());
    }
}
