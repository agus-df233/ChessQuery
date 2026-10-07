package cl.chessquery.game.room;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.game.room.RoomDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

/** Salas de juego: el organizador las crea y administra; los jugadores entran con el código. */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService rooms;
    private final RoomQueries queries;

    @GetMapping("/mine")
    public Mine mine(@CurrentUser UserPrincipal me) {
        return queries.mine(me.playerId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomView create(@CurrentUser UserPrincipal me, @Valid @RequestBody RoomRequest req) {
        return rooms.create(me, req);
    }

    @GetMapping(value = "/{id}", params = "!afterVersion")
    public RoomView get(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return queries.view(me.playerId(), id);
    }

    /** Long polling (respaldo del WebSocket): responde cuando la sala pase de {@code afterVersion}, o a los 25 s. */
    @GetMapping(value = "/{id}", params = "afterVersion")
    public DeferredResult<RoomView> watch(@CurrentUser UserPrincipal me, @PathVariable long id, @RequestParam long afterVersion) {
        return queries.watch(me.playerId(), id, afterVersion);
    }

    @PutMapping("/{id}")
    public RoomView update(@CurrentUser UserPrincipal me, @PathVariable long id, @Valid @RequestBody RoomRequest req) {
        return rooms.update(me, id, req);
    }

    @PostMapping("/join")
    public RoomView join(@CurrentUser UserPrincipal me, @Valid @RequestBody JoinRequest req) {
        return rooms.join(me, req);
    }

    /** El organizador saca a un jugador; si es el propio jugador, sale de la sala (204). */
    @DeleteMapping("/{id}/members/{playerId}")
    public ResponseEntity<RoomView> removeMember(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable long playerId) {
        RoomView view = rooms.removeMember(me, id, playerId);
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    @PutMapping("/{id}/boards/{boardNo}")
    public RoomView assign(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable int boardNo,
                           @RequestBody AssignRequest req) {
        return rooms.assign(me, id, boardNo, req);
    }

    @PostMapping("/{id}/boards/{boardNo}/start")
    public RoomView start(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable int boardNo) {
        return rooms.start(me, id, boardNo);
    }

    @PostMapping("/{id}/start")
    public RoomView startAll(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return rooms.startAll(me, id);
    }

    @PostMapping("/{id}/boards/{boardNo}/rematch")
    public RoomView rematch(@CurrentUser UserPrincipal me, @PathVariable long id, @PathVariable int boardNo) {
        return rooms.rematch(me, id, boardNo);
    }

    @PostMapping("/{id}/close")
    public RoomView close(@CurrentUser UserPrincipal me, @PathVariable long id) {
        return rooms.close(me, id);
    }
}
