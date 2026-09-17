package cl.chessquery.users.player;

import java.util.Locale;

/**
 * Normalización de emails en un solo punto: la columna es única y se usa para
 * reconocer identidades (provisión, claim de provisorios, invitaciones). Si una
 * escritura guarda {@code Foo@Mail.com} y una lectura busca {@code foo@mail.com},
 * se duplica el perfil.
 */
public final class Emails {

    private Emails() {}

    /** Trim + minúsculas; null si queda vacío. */
    public static String normalize(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return e.isEmpty() ? null : e;
    }
}
