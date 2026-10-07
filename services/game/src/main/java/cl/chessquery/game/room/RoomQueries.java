package cl.chessquery.game.room;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.game.api.GameDtos.GameView;
import cl.chessquery.game.domain.Game;
import cl.chessquery.game.domain.GameRepository;
import cl.chessquery.game.room.RoomDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lecturas de salas. Una sala solo la ven su organizador y sus miembros (son clases con menores: no es pública);
 * los nombres son los públicos que dio users al entrar (apellido de menores abreviado).
 */
@Service
@RequiredArgsConstructor
public class RoomQueries {

    private final RoomRepository rooms;
    private final RoomMemberRepository members;
    private final RoomBoardRepository boards;
    private final GameRepository games;
    private final RoomNotifier notifier;
    private final Clock clock;

    @Value("${chessquery.games.long-poll-timeout:25s}")
    private Duration longPollTimeout;

    @Transactional(readOnly = true)
    public RoomView view(long playerId, long roomId) {
        Room room = require(roomId);
        List<RoomMember> people = members.findByRoomIdOrderByJoinedAt(roomId);
        boolean organizer = room.isOwnedBy(playerId);
        if (!organizer && people.stream().noneMatch(m -> m.getPlayerId() == playerId)) {
            throw ApiException.forbidden("NOT_IN_ROOM", "Entra a la sala con su código para verla");
        }
        return build(room, people, playerId, organizer);
    }

    /** Puede ver la sala: su organizador o un miembro (sin excepciones: lo usa el WebSocket dentro de su transacción). */
    @Transactional(readOnly = true)
    public boolean canView(long playerId, long roomId) {
        return rooms.findById(roomId)
                .map(r -> r.isOwnedBy(playerId) || members.existsByRoomIdAndPlayerId(roomId, playerId))
                .orElse(false);
    }

    /** Long polling: responde cuando la sala pase de {@code afterVersion}, o a los 25 s con el estado actual. */
    public DeferredResult<RoomView> watch(long playerId, long roomId, long afterVersion) {
        DeferredResult<RoomView> result = new DeferredResult<>(longPollTimeout.toMillis());
        RoomView now = view(playerId, roomId);
        if (now.version() > afterVersion || now.status() == RoomStatus.CLOSED) {
            result.setResult(now);
            return result;
        }
        Runnable onChange = () -> result.setResult(view(playerId, roomId));
        notifier.await(roomId, onChange);
        result.onTimeout(() -> result.setResult(view(playerId, roomId)));
        result.onCompletion(() -> notifier.forget(roomId, onChange));
        RoomView again = view(playerId, roomId); // por si cambió entre la primera lectura y el registro
        if (again.version() > afterVersion) result.setResult(again);
        return result;
    }

    @Transactional(readOnly = true)
    public Mine mine(long playerId) {
        return new Mine(summaries(rooms.findTop20ByOrganizerIdOrderByCreatedAtDesc(playerId), true),
                summaries(rooms.findJoinedBy(playerId), false));
    }

    Room require(long roomId) {
        return rooms.findById(roomId)
                .orElseThrow(() -> ApiException.notFound("ROOM_NOT_FOUND", "Sala " + roomId + " no encontrada"));
    }

    private RoomView build(Room room, List<RoomMember> people, long playerId, boolean organizer) {
        List<RoomBoard> tables = boards.findByRoomIdOrderByBoardNo(room.getId());
        Map<Long, String> names = people.stream()
                .collect(Collectors.toMap(RoomMember::getPlayerId, RoomMember::getPublicName, (a, b) -> a));
        Map<Long, Game> byId = games.findAllById(tables.stream().map(RoomBoard::getGameId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Game::getId, Function.identity()));
        List<BoardView> boardViews = tables.stream().map(b -> new BoardView(b.getBoardNo(),
                seat(b.getWhitePlayerId(), names), seat(b.getBlackPlayerId(), names),
                b.getGameId() == null || !byId.containsKey(b.getGameId()) ? null
                        : GameView.of(byId.get(b.getGameId()), clock.instant()))).toList();
        RoomBoard mine = tables.stream().filter(b -> b.seats(playerId)).findFirst().orElse(null);
        return new RoomView(room.getId(), room.getName(), room.getCode(), room.getStatus(), room.getBoards(),
                room.getMaxPlayers(), room.getInitialSeconds(), room.getIncrementSeconds(), room.category(),
                room.getVersion(), organizer, mine == null ? null : mine.getBoardNo(),
                mine == null ? null : mine.getGameId(),
                people.stream().map(m -> member(m, tables)).toList(), boardViews);
    }

    private static Seat seat(Long playerId, Map<Long, String> names) {
        return playerId == null ? null : new Seat(playerId, names.getOrDefault(playerId, "Jugador"));
    }

    private static MemberView member(RoomMember m, List<RoomBoard> tables) {
        for (RoomBoard b : tables) {
            if (b.seats(m.getPlayerId())) {
                String color = m.getPlayerId().equals(b.getWhitePlayerId()) ? "WHITE" : "BLACK";
                return new MemberView(m.getPlayerId(), m.getPublicName(), b.getBoardNo(), color);
            }
        }
        return new MemberView(m.getPlayerId(), m.getPublicName(), null, null);
    }

    /** El código solo se muestra en las salas propias (el jugador ya lo conoce y no lo necesita en la lista). */
    private List<RoomSummary> summaries(List<Room> list, boolean own) {
        return list.stream().map(r -> new RoomSummary(r.getId(), r.getName(), own ? r.getCode() : null, r.getStatus(),
                r.getBoards(), r.getMaxPlayers(), members.countByRoomId(r.getId()), r.category(), r.getCreatedAt()))
                .toList();
    }
}
