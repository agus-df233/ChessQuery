package cl.chessquery.users.friends;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Amigos: se pide y se acepta; una fila por par; rechazar o dejar de ser amigos borra la fila;
 * si los dos se piden a la vez, la segunda solicitud cierra el trato en vez de chocar.
 * La identidad del que actúa siempre viene del token (controlador), nunca del body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendships;
    private final PlayerRepository players;
    private final EventPublisher events;

    /** Proyección liviana a propósito: ser amigo no da acceso a la PII del otro. */
    public record Friend(Long playerId, String firstName, String lastName, String clubName,
                         Integer eloNational, Integer eloPlatform, Instant since) {}

    public record Request(Long requestId, Long playerId, String firstName, String lastName,
                          Integer eloNational, String direction, Instant createdAt) {}

    /** Estado con otro jugador: NONE / PENDING_OUT / PENDING_IN / FRIENDS, más el id de la fila si hay. */
    public record Status(String status, Long requestId) {
        public static final String NONE = "NONE", PENDING_OUT = "PENDING_OUT", PENDING_IN = "PENDING_IN", FRIENDS = "FRIENDS";
    }

    // ── Lecturas ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Friend> listFriends(Long playerId) {
        List<Friendship> rows = friendships.findByPlayerAndStatus(playerId, Friendship.Status.ACCEPTED);
        Map<Long, Player> others = load(rows, playerId);
        return rows.stream().map(f -> {
            Player o = others.get(f.otherSide(playerId));
            return o == null ? null : new Friend(o.getId(), o.getFirstName(), o.getLastName(),
                    o.getClub() != null ? o.getClub().getName() : null, o.getEloNational(), o.getEloPlatform(),
                    f.getRespondedAt() != null ? f.getRespondedAt() : f.getCreatedAt());
        }).filter(java.util.Objects::nonNull).toList();
    }

    /** @param incoming true = las que me llegaron; false = las que envié. */
    @Transactional(readOnly = true)
    public List<Request> listRequests(Long playerId, boolean incoming) {
        List<Friendship> rows = incoming
                ? friendships.findByAddresseeIdAndStatusOrderByCreatedAtDesc(playerId, Friendship.Status.PENDING)
                : friendships.findByRequesterIdAndStatusOrderByCreatedAtDesc(playerId, Friendship.Status.PENDING);
        Map<Long, Player> others = load(rows, playerId);
        return rows.stream().map(f -> {
            Player o = others.get(f.otherSide(playerId));
            return o == null ? null : new Request(f.getId(), o.getId(), o.getFirstName(), o.getLastName(),
                    o.getEloNational(), incoming ? "INCOMING" : "OUTGOING", f.getCreatedAt());
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public Status statusWith(Long playerId, Long otherId) {
        if (playerId.equals(otherId)) return new Status(Status.NONE, null);
        return friendships.findByPair(playerId, otherId).map(f -> {
            if (f.getStatus() == Friendship.Status.ACCEPTED) return new Status(Status.FRIENDS, f.getId());
            return new Status(f.getRequesterId().equals(playerId) ? Status.PENDING_OUT : Status.PENDING_IN, f.getId());
        }).orElseGet(() -> new Status(Status.NONE, null));
    }

    @Transactional(readOnly = true)
    public boolean areFriends(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) return false;
        return friendships.findByPair(a, b).filter(f -> f.getStatus() == Friendship.Status.ACCEPTED).isPresent();
    }

    // ── Escrituras ───────────────────────────────────────────────────────────

    @Transactional
    public Status request(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) throw ApiException.badRequest("SELF_FRIENDSHIP", "No puedes agregarte a ti mismo");
        if (!players.existsById(addresseeId)) throw ApiException.notFound("PLAYER_NOT_FOUND", "Jugador no encontrado");

        var existing = friendships.findByPair(requesterId, addresseeId);
        if (existing.isPresent()) {
            Friendship f = existing.get();
            if (f.getStatus() == Friendship.Status.ACCEPTED) throw ApiException.conflict("ALREADY_FRIENDS", "Ya son amigos");
            if (f.getRequesterId().equals(requesterId)) throw ApiException.conflict("REQUEST_ALREADY_SENT", "Ya enviaste una solicitud");
            // Solicitud cruzada: el otro ya me había pedido; esto la acepta.
            return accept(f, requesterId);
        }
        Friendship created = new Friendship(requesterId, addresseeId);
        try {
            friendships.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("REQUEST_ALREADY_SENT", "Ya existe una solicitud entre estos jugadores");
        }
        events.publish(UsersEvents.FRIEND_REQUEST_CREATED, Map.of("requestId", created.getId(),
                "fromPlayerId", requesterId, "fromName", nameOf(requesterId), "toPlayerId", addresseeId));
        return new Status(Status.PENDING_OUT, created.getId());
    }

    @Transactional
    public Status accept(Long requestId, Long playerId) {
        Friendship f = pending(requestId);
        if (!f.getAddresseeId().equals(playerId)) throw ApiException.forbidden("NOT_ADDRESSEE", "Solo el destinatario puede aceptar");
        return accept(f, playerId);
    }

    @Transactional
    public void decline(Long requestId, Long playerId) {
        Friendship f = pending(requestId);
        if (!f.getAddresseeId().equals(playerId)) throw ApiException.forbidden("NOT_ADDRESSEE", "Solo el destinatario puede rechazar");
        friendships.delete(f);
    }

    /** Dejar de ser amigos o cancelar mi solicitud; simétrico. */
    @Transactional
    public void remove(Long playerId, Long otherId) {
        Friendship f = friendships.findByPair(playerId, otherId)
                .orElseThrow(() -> ApiException.notFound("FRIENDSHIP_NOT_FOUND", "No existe relación con ese jugador"));
        friendships.delete(f);
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private Status accept(Friendship f, Long acceptedBy) {
        f.accept();
        friendships.save(f);
        events.publish(UsersEvents.FRIEND_REQUEST_ACCEPTED, Map.of("requestId", f.getId(),
                "fromPlayerId", f.getRequesterId(), "toPlayerId", f.getAddresseeId(), "acceptedName", nameOf(acceptedBy)));
        return new Status(Status.FRIENDS, f.getId());
    }

    private Friendship pending(Long requestId) {
        Friendship f = friendships.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "La solicitud no existe"));
        if (f.getStatus() != Friendship.Status.PENDING) throw ApiException.conflict("REQUEST_NOT_PENDING", "La solicitud ya fue respondida");
        return f;
    }

    private String nameOf(Long playerId) {
        return players.findById(playerId).map(Player::fullName).orElse("");
    }

    /** Un solo viaje a la BD para todos los "otros" de la lista. */
    private Map<Long, Player> load(List<Friendship> rows, Long playerId) {
        List<Long> ids = rows.stream().map(f -> f.otherSide(playerId)).toList();
        if (ids.isEmpty()) return Map.of();
        return players.findAllById(ids).stream().collect(Collectors.toMap(Player::getId, Function.identity()));
    }
}
