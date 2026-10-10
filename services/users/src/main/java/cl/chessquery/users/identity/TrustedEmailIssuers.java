package cl.chessquery.users.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emisores de tokens cuyo claim {@code email} ya viene verificado. El IdP de ChessQuery solo admite Google (Cognito
 * en el lab; Entra con Google o código de un solo uso en la cuenta propia), así que por defecto se confía en el issuer
 * configurado.
 * Cualquier otro emisor necesita traer {@code email_verified=true} para que su email se use como identidad.
 */
@Component
public class TrustedEmailIssuers {

    private final Set<String> issuers;

    public TrustedEmailIssuers(@Value("${chessquery.identity.trusted-email-issuers:}") String csv) {
        this.issuers = Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(TrustedEmailIssuers::normalize).collect(Collectors.toUnmodifiableSet());
    }

    public boolean emailIsVerified(Map<String, Object> claims) {
        Object verified = claims.get("email_verified");
        if (Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified))) return true;
        Object iss = claims.get("iss");
        return iss != null && issuers.contains(normalize(iss.toString()));
    }

    private static String normalize(String issuer) {
        return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }
}
