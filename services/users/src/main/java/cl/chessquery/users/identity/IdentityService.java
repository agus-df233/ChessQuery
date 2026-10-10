package cl.chessquery.users.identity;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.organization.OrganizationRepository;
import cl.chessquery.users.player.Emails;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Identidad interna: traduce el {@code sub} del IdP en {@code player.id} y provisiona al primer
 * acceso (JIT). Reemplaza al webhook de registro y al resolver del gateway de la v2.
 *
 * <p>Orden al provisionar: (1) fila con ese sub → listo; (2) fila con el mismo email →
 * se adopta: si era un provisorio del roster de un club, el jugador <b>reclama</b> esa fila y
 * conserva su historial (ADR-0004 v2), avisando con {@code player.claimed}; (3) fila nueva.
 *
 * <p>La adopción por email solo ocurre si el email está <b>verificado</b>: el token viene de un emisor
 * confiable ({@code chessquery.identity.trusted-email-issuers}, por defecto el IdP configurado, cuyo único proveedor
 * es Google, que verifica el correo) o trae {@code email_verified=true}. Así, un proveedor que no
 * verifique correos no puede usarse para quedarse con el perfil de otra persona.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService implements PlayerIdentityResolver {

    private final PlayerRepository players;
    private final OrganizationRepository organizations;
    private final EventPublisher events;
    private final TrustedEmailIssuers trustedIssuers;
    private final PlayerProvisioner provisioner;

    @Override
    @Transactional
    public ResolvedIdentity resolve(String subject, Map<String, Object> claims) {
        Player player = players.findByExternalSubject(subject).orElseGet(() -> adoptOrCreate(subject, claims));
        return identityOf(player);
    }

    /** Solo lectura: 404 si el sujeto no tiene jugador (lo usan otros servicios antes de provisionar). */
    @Transactional(readOnly = true)
    public ResolvedIdentity find(String subject) {
        return players.findByExternalSubject(subject).map(this::identityOf)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "Sujeto sin jugador"));
    }

    private ResolvedIdentity identityOf(Player p) {
        Long orgId = organizations.findByOwnerPlayerId(p.getId()).map(o -> o.getId()).orElse(null);
        return new ResolvedIdentity(p.getId(), orgId);
    }

    private Player adoptOrCreate(String subject, Map<String, Object> claims) {
        String email = Emails.normalize(str(claims.get("email")));
        boolean verified = trustedIssuers.emailIsVerified(claims);
        Optional<Player> byEmail = email == null || !verified ? Optional.empty() : players.findByEmail(email);
        if (byEmail.isPresent()) {
            return adopt(byEmail.get(), subject, email);
        }
        Names names = Names.from(claims);
        Player fresh = Player.builder()
                .externalSubject(subject)
                // Un email sin verificar no se guarda: podría ser de otra persona y bloquear su registro.
                .email(verified ? email : null)
                .firstName(names.first())
                .lastName(names.last())
                .displayName(str(claims.get("name")))
                .build();
        try {
            Player saved = provisioner.insert(fresh);
            events.publish(UsersEvents.PLAYER_PROVISIONED, payload(saved));
            log.info("Jugador {} provisionado para sujeto {}", saved.getId(), subject);
            return saved;
        } catch (DataIntegrityViolationException race) {
            // Dos requests simultáneos del mismo usuario nuevo: gana el primero.
            return players.findByExternalSubject(subject).orElseThrow(() -> race);
        }
    }

    /** La cuenta nueva se queda con la fila que ya existía (provisoria o federada). */
    private Player adopt(Player existing, String subject, String email) {
        existing.setExternalSubject(subject);
        boolean claimed = existing.isProvisional();
        if (claimed) {
            existing.setProvisional(false);
            existing.setActive(true);
        }
        Player saved = players.saveAndFlush(existing);
        Map<String, Object> payload = payload(saved);
        payload.put("organizerId", saved.getCreatedByOrganizerId());
        events.publish(claimed ? UsersEvents.PLAYER_CLAIMED : UsersEvents.PLAYER_PROVISIONED, payload);
        log.info("Sujeto {} adoptó la fila {} ({})", subject, saved.getId(), claimed ? "provisorio reclamado" : "email existente");
        return saved;
    }

    private static Map<String, Object> payload(Player p) {
        Map<String, Object> m = new HashMap<>();
        m.put("playerId", p.getId());
        m.put("email", p.getEmail() == null ? "" : p.getEmail());
        m.put("fullName", p.fullName());
        return m;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * Nombre y apellido del token. Si el IdP no manda {@code given_name}/{@code family_name} (optional claims no
     * configurados), se separa {@code name}: la primera palabra es el nombre y el resto el apellido.
     */
    record Names(String first, String last) {
        static Names from(Map<String, Object> claims) {
            String given = blankToNull(str(claims.get("given_name")));
            String family = blankToNull(str(claims.get("family_name")));
            if (given != null) return new Names(given, family == null ? "" : family);
            String full = blankToNull(str(claims.get("name")));
            if (full == null) return new Names("Jugador", "");
            String[] parts = full.split("\\s+", 2);
            return new Names(parts[0], parts.length > 1 ? parts[1] : "");
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
