package cl.chessquery.users.player;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.users.organization.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Implementación local (BD) de la resolución sub → playerId. Provisión JIT: el primer
 * request autenticado de un usuario nuevo crea su Player. Reemplaza al webhook de Supabase
 * y al PlayerIdResolver del gateway de la v2.
 */
@Service
public class LocalPlayerIdentityResolver implements PlayerIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalPlayerIdentityResolver.class);

    private final PlayerRepository players;
    private final OrganizationRepository organizations;

    public LocalPlayerIdentityResolver(PlayerRepository players, OrganizationRepository organizations) {
        this.players = players;
        this.organizations = organizations;
    }

    @Override
    @Transactional
    public ResolvedIdentity resolve(String subject, Map<String, Object> claims) {
        Player player = players.findByExternalSubject(subject).orElseGet(() -> provision(subject, claims));
        Long orgId = organizations.findByOwnerPlayerId(player.getId()).map(o -> o.getId()).orElse(null);
        return new ResolvedIdentity(player.getId(), orgId);
    }

    @Transactional(readOnly = true)
    public ResolvedIdentity find(String subject) {
        Player player = players.findByExternalSubject(subject)
                .orElseThrow(() -> cl.chessquery.common.api.ApiException.notFound("PLAYER_NOT_FOUND", "Sujeto sin jugador"));
        Long orgId = organizations.findByOwnerPlayerId(player.getId()).map(o -> o.getId()).orElse(null);
        return new ResolvedIdentity(player.getId(), orgId);
    }

    private Player provision(String subject, Map<String, Object> claims) {
        Player candidate = Player.fromIdentity(subject,
                str(claims.get("email")), str(claims.get("given_name")),
                str(claims.get("family_name")), str(claims.get("name")));
        try {
            Player saved = players.saveAndFlush(candidate);
            log.info("Jugador {} provisionado para sujeto {}", saved.getId(), subject);
            return saved;
        } catch (DataIntegrityViolationException race) {
            // Dos requests concurrentes del mismo usuario nuevo: gana el primero.
            return players.findByExternalSubject(subject).orElseThrow(() -> race);
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
