package cl.chessquery.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolución sub → playerId contra users por HTTP, con caché Caffeine (5 min, 10k).
 * Hereda el contrato del PlayerIdResolver del gateway v2: 404 → provisión JIT.
 */
public class HttpPlayerIdentityResolver implements PlayerIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(HttpPlayerIdentityResolver.class);

    private final RestClient client;
    private final Cache<String, ResolvedIdentity> cache;

    public HttpPlayerIdentityResolver(RestClient client) {
        this.client = client;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
    }

    @Override
    public ResolvedIdentity resolve(String subject, Map<String, Object> claims) {
        ResolvedIdentity cached = cache.getIfPresent(subject);
        if (cached != null) {
            return cached;
        }
        ResolvedIdentity resolved = fetch(subject, claims);
        cache.put(subject, resolved);
        return resolved;
    }

    public void evict(String subject) {
        cache.invalidate(subject);
    }

    private ResolvedIdentity fetch(String subject, Map<String, Object> claims) {
        try {
            return client.get()
                    .uri("/internal/players/by-subject/{sub}", subject)
                    .retrieve()
                    .body(ResolvedIdentity.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw e;
            }
            log.info("Sujeto {} sin jugador; provisionando", subject);
            Map<String, Object> body = new HashMap<>();
            body.put("subject", subject);
            body.put("email", claims.get("email"));
            body.put("firstName", claims.get("given_name"));
            body.put("lastName", claims.get("family_name"));
            body.put("displayName", claims.get("name"));
            return client.post()
                    .uri("/internal/players/provision")
                    .body(body)
                    .retrieve()
                    .body(ResolvedIdentity.class);
        }
    }
}
