package cl.chessquery.auth;

import java.util.Set;

/**
 * Identidad resuelta del request. {@code playerId} es la clave interna (numérica) que usan
 * torneos, partidas y notificaciones; {@code subject} es el {@code sub} del IdP.
 * ORGANIZER no es un rol del IdP: es "dueño de una organización" según users.
 */
public record UserPrincipal(
        long playerId,
        String subject,
        String email,
        Long organizationId,
        Set<String> roles
) {
    public boolean isOrganizer() {
        return organizationId != null;
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
