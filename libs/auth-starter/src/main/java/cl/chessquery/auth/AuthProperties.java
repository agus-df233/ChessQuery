package cl.chessquery.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de identidad. El issuer y la audiencia vienen del app registration
 * de Entra External ID (Google entra federado por ese mismo tenant, así que hay un
 * único emisor). Los servicios no conocen usuarios ni contraseñas: solo validan tokens.
 */
@ConfigurationProperties(prefix = "chessquery.auth")
public record AuthProperties(
        /** URL base del servicio users para resolver sub → playerId (vacío en el propio users). */
        String usersUrl,
        /** Secreto compartido para rutas /internal/** entre servicios (Secrets Manager en cloud). */
        String internalToken,
        /** Claim de Entra con app roles (p. ej. ADMIN). */
        String rolesClaim,
        /** Cabecera X-Origin-Verify para llamar a users a través del ALB (vacío en local). */
        String originSecret
) {
    public AuthProperties {
        if (usersUrl == null) usersUrl = "";
        if (internalToken == null) internalToken = "";
        if (rolesClaim == null || rolesClaim.isBlank()) rolesClaim = "roles";
        if (originSecret == null) originSecret = "";
    }
}
