package cl.chessquery.users.privacy;

import cl.chessquery.users.player.PlayerDtos.PublicProfile;
import cl.chessquery.users.player.PlayerService;
import cl.chessquery.users.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * Vistas sin login bajo {@code /api/public/**} (permitAll en auth-starter): ranking nacional y
 * perfil público, pensadas para compartir por QR y cachear en CloudFront. Las mismas proyecciones
 * para terceros que con sesión: sin PII y con los menores abreviados.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final RankingService ranking;
    private final PlayerService players;

    @GetMapping("/ranking")
    public ResponseEntity<List<RankingService.Entry>> ranking(@RequestParam(required = false) String category,
                                                              @RequestParam(required = false) String region,
                                                              @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok().cacheControl(CACHE).body(ranking.ranking(category, region, limit));
    }

    @GetMapping("/players/{id}")
    public ResponseEntity<PublicProfile> player(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CACHE).body(players.publicProfile(id));
    }
}
