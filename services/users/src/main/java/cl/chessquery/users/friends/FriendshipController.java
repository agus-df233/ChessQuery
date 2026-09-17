package cl.chessquery.users.friends;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Amigos y solicitudes del jugador autenticado. */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService service;

    @GetMapping
    public List<FriendshipService.Friend> list(@CurrentUser UserPrincipal user) {
        return service.listFriends(user.playerId());
    }

    /** @param direction {@code incoming} (recibidas) u {@code outgoing} (enviadas). */
    @GetMapping("/requests")
    public List<FriendshipService.Request> requests(@CurrentUser UserPrincipal user,
                                                    @RequestParam(defaultValue = "incoming") String direction) {
        return switch (direction.toLowerCase()) {
            case "incoming" -> service.listRequests(user.playerId(), true);
            case "outgoing" -> service.listRequests(user.playerId(), false);
            default -> throw ApiException.badRequest("INVALID_DIRECTION", "direction debe ser incoming u outgoing");
        };
    }

    public record RequestBody(@NotNull Long addresseeId) {}

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendshipService.Status request(@CurrentUser UserPrincipal user, @Valid @org.springframework.web.bind.annotation.RequestBody RequestBody body) {
        return service.request(user.playerId(), body.addresseeId());
    }

    @PostMapping("/requests/{requestId}/accept")
    public FriendshipService.Status accept(@CurrentUser UserPrincipal user, @PathVariable Long requestId) {
        return service.accept(requestId, user.playerId());
    }

    @PostMapping("/requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@CurrentUser UserPrincipal user, @PathVariable Long requestId) {
        service.decline(requestId, user.playerId());
    }

    @DeleteMapping("/{otherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUser UserPrincipal user, @PathVariable Long otherId) {
        service.remove(user.playerId(), otherId);
    }

    @GetMapping("/status/{otherId}")
    public FriendshipService.Status status(@CurrentUser UserPrincipal user, @PathVariable Long otherId) {
        return service.statusWith(user.playerId(), otherId);
    }
}
