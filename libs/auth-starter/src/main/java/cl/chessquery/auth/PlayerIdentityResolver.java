package cl.chessquery.auth;

import java.util.Map;

/**
 * Traduce la identidad del IdP en la identidad interna. El servicio users la implementa
 * contra su BD; los demás servicios usan {@link HttpPlayerIdentityResolver} (HTTP + caché).
 * Provisión JIT: si el sujeto no existe todavía, users crea el jugador con los claims.
 */
public interface PlayerIdentityResolver {

    /**
     * @param subject {@code sub} del token
     * @param claims  claims útiles para provisionar (email, name, given_name, family_name)
     */
    ResolvedIdentity resolve(String subject, Map<String, Object> claims);

    record ResolvedIdentity(long playerId, Long organizationId) {}
}
