package cl.chessquery.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de identidad. El issuer y la audiencia son los del IdP: el user pool de Cognito en el Learner Lab
 * (Google federado; audiencia = client id de la web) o un tenant de Entra External ID en la cuenta propia. Los
 * servicios no conocen usuarios ni contraseñas: solo validan tokens (ADR-0002).
 */
@ConfigurationProperties(prefix = "chessquery.auth")
public record AuthProperties(
        /** URL base del servicio users para resolver sub → playerId (vacío en el propio users). */
        String usersUrl,
        /** Secreto compartido para rutas /internal/** entre servicios (Secrets Manager en cloud). */
        String internalToken,
        /** Claim con los roles de la app (p. ej. ADMIN), si el IdP los emite. */
        String rolesClaim,
        /** Cabecera X-Origin-Verify para llamar a users a través del ALB (vacío en local). */
        String originSecret,
        /**
         * Rutas extra sin Bearer, que valida el propio servicio (p. ej. {@code /ws}: el navegador no puede mandar
         * cabeceras al abrir un WebSocket, así que el token viaja en la query y lo verifica game).
         */
        java.util.List<String> publicPaths
) {
    public AuthProperties {
        if (usersUrl == null) usersUrl = "";
        if (internalToken == null) internalToken = "";
        if (rolesClaim == null || rolesClaim.isBlank()) rolesClaim = "roles";
        if (originSecret == null) originSecret = "";
        publicPaths = publicPaths == null ? java.util.List.of() : java.util.List.copyOf(publicPaths);
    }
}
