package cl.chessquery.users.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * HMAC-SHA256 de identificadores personales (RUT, FIDE id, id federativo) con un <i>pepper</i> que
 * vive fuera de la base de datos (SSM en la nube). Sirve para hacer match con fuentes externas y
 * para la lista de supresión sin guardar el identificador en claro: con solo la BD no se revierte.
 */
@Component
public class IdentifierHasher {

    private final SecretKeySpec key;

    public IdentifierHasher(@Value("${chessquery.privacy.pepper}") String pepper) {
        if (pepper == null || pepper.length() < 16) {
            throw new IllegalStateException("chessquery.privacy.pepper debe tener al menos 16 caracteres");
        }
        this.key = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** RUT normalizado: sin puntos, espacios ni guion, DV en mayúscula ("12.345.678-k" → "12345678K"). */
    public static String normalizeRut(String rut) {
        return rut == null ? null : rut.replaceAll("[.\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public String rut(String rut) {
        String n = normalizeRut(rut);
        return n == null || n.isEmpty() ? null : hmac("rut:" + n);
    }

    public String fideId(String id) {
        return id == null || id.isBlank() ? null : hmac("fide:" + id.trim());
    }

    public String federationId(String id) {
        return id == null || id.isBlank() ? null : hmac("fed:" + id.trim());
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }
}
