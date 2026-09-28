package cl.chessquery.users.privacy;

import cl.chessquery.users.player.Player;

import java.time.Year;

/**
 * Nombre que se muestra a terceros (ranking, búsqueda, perfil público). A los menores de edad se
 * les abrevia el apellido ("Juan P.") salvo que hayan reclamado su cuenta y, si tienen menos de
 * 14 años, además exista consentimiento parental registrado (Ley 21.719: datos de niños, niñas y
 * adolescentes). Sin año de nacimiento se asume adulto, igual que las categorías.
 */
public final class PublicNames {

    public static final int ADULT_AGE = 18;
    public static final int SELF_CONSENT_AGE = 14;

    private PublicNames() {}

    /** Edad que cumple el jugador este año (la regla de las categorías de ajedrez). */
    static Integer ageThisYear(Player p) {
        return p.getBirthYear() == null ? null : Year.now().getValue() - p.getBirthYear();
    }

    public static boolean mustAbbreviate(Player p) {
        Integer age = ageThisYear(p);
        if (age == null || age > ADULT_AGE) return false;
        if (!p.hasAccount()) return true;
        return age <= SELF_CONSENT_AGE && p.getParentalConsentAt() == null;
    }

    public static String lastName(Player p) {
        String last = p.getLastName();
        if (!mustAbbreviate(p) || last == null || last.isBlank()) return last;
        return last.trim().substring(0, 1).toUpperCase() + ".";
    }

    /** El display name es texto libre del jugador: se oculta mientras aplique la abreviatura. */
    public static String displayName(Player p) {
        return mustAbbreviate(p) ? null : p.getDisplayName();
    }
}
