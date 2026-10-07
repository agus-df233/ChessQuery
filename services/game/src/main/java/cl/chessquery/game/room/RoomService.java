package cl.chessquery.game.room;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.game.GameService;
import cl.chessquery.game.domain.Game;
import cl.chessquery.game.domain.GameRepository;
import cl.chessquery.game.domain.GameStatus;
import cl.chessquery.game.room.RoomDtos.AssignRequest;
import cl.chessquery.game.room.RoomDtos.JoinRequest;
import cl.chessquery.game.room.RoomDtos.RoomRequest;
import cl.chessquery.game.room.RoomDtos.RoomView;
import cl.chessquery.game.users.UsersClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Salas de juego: el organizador (un colegio, un club) despliega tableros para una clase o una práctica.
 * <ul>
 *   <li>Los jugadores entran con el código de la sala (solo con su cuenta); el organizador los asigna a los tableros y
 *       los que no tienen tablero miran como espectadores hasta que él cambie la configuración.</li>
 *   <li>Cada tablero en juego es una partida normal ({@link GameService#startRoomGame}), siempre sin rating.</li>
 *   <li>Un tablero con partida en curso no se toca: ni reasignar, ni quitar, ni sacar a sus jugadores.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    static final int MAX_OPEN_ROOMS = 3;
    private static final int CODE_ATTEMPTS = 5;

    private final RoomRepository rooms;
    private final RoomMemberRepository members;
    private final RoomBoardRepository boards;
    private final GameRepository games;
    private final GameService gameService;
    private final UsersClient users;
    private final RoomCodes codes;
    private final JoinAttempts attempts;
    private final RoomQueries queries;
    private final RoomNotifier notifier;
    private final Clock clock;

    @Transactional
    public RoomView create(UserPrincipal me, RoomRequest req) {
        if (!me.isOrganizer()) throw ApiException.forbidden("NOT_ORGANIZER", "Crea tu club para abrir salas de juego");
        if (rooms.countByOrganizationIdAndStatus(me.organizationId(), RoomStatus.OPEN) >= MAX_OPEN_ROOMS) {
            throw ApiException.conflict("ROOM_LIMIT", "Tu club puede tener " + MAX_OPEN_ROOMS + " salas abiertas a la vez");
        }
        Room room = new Room();
        room.setOrganizationId(me.organizationId());
        room.setOrganizerId(me.playerId());
        room.setCode(freeCode());
        room.setCreatedAt(clock.instant());
        configure(room, req, 0);
        rooms.save(room);
        for (int n = 1; n <= room.getBoards(); n++) boards.save(new RoomBoard(room.getId(), n));
        log.info("Club {} abrió la sala {} ({} tableros)", me.organizationId(), room.getId(), room.getBoards());
        return queries.view(me.playerId(), room.getId());
    }

    /** Cambia nombre, ritmo (para las partidas que empiecen después), tableros y cupo. */
    @Transactional
    public RoomView update(UserPrincipal me, long id, RoomRequest req) {
        Room room = ownedOpen(me, id);
        long joined = members.countByRoomId(id);
        configure(room, req, joined);
        resizeBoards(room);
        return changed(room, me);
    }

    @Transactional
    public RoomView join(UserPrincipal me, JoinRequest req) {
        attempts.register(me.playerId());
        String code = RoomCodes.normalize(req.code());
        Room room = code == null ? null : rooms.findByCodeAndStatus(code, RoomStatus.OPEN).orElse(null);
        // Mismo error si el código no existe o la sala cerró: no se revela cuál de los dos
        if (room == null) throw ApiException.notFound("ROOM_NOT_FOUND", "No hay una sala abierta con ese código");
        if (room.isOwnedBy(me.playerId()) || members.existsByRoomIdAndPlayerId(room.getId(), me.playerId())) {
            return queries.view(me.playerId(), room.getId());
        }
        if (members.countByRoomId(room.getId()) >= room.getMaxPlayers()) {
            throw ApiException.conflict("ROOM_FULL", "La sala está completa");
        }
        String name = users.player(me.playerId()).publicName();
        members.save(new RoomMember(room.getId(), me.playerId(), name, clock.instant()));
        return changed(room, me);
    }

    /** El organizador saca a un miembro, o el propio jugador se va. No mientras juega una partida de la sala. */
    @Transactional
    public RoomView removeMember(UserPrincipal me, long id, long playerId) {
        Room room = queries.require(id);
        boolean self = me.playerId() == playerId;
        if (!self && !room.isOwnedBy(me.playerId())) throw notOrganizer();
        RoomMember member = members.findById(new RoomMember.Key(id, playerId))
                .orElseThrow(() -> ApiException.notFound("NOT_A_MEMBER", "Ese jugador no está en la sala"));
        for (RoomBoard b : boards.findByRoomIdOrderByBoardNo(id)) {
            if (!b.seats(playerId)) continue;
            if (inPlay(b)) throw ApiException.conflict("PLAYER_IN_GAME", "Ese jugador está jugando una partida");
            b.clearSeat(playerId);
        }
        members.delete(member);
        room.touch();
        rooms.save(room);
        notifier.changedAfterCommit(id);
        return self ? null : queries.view(me.playerId(), id);
    }

    /**
     * Asigna (o libera, con null) los puestos de un tablero. Si un jugador estaba en otro tablero sin partida en
     * curso, se mueve; si está jugando, no.
     */
    @Transactional
    public RoomView assign(UserPrincipal me, long id, int boardNo, AssignRequest req) {
        Room room = ownedOpen(me, id);
        RoomBoard board = board(id, boardNo);
        if (inPlay(board)) throw ApiException.conflict("BOARD_IN_PLAY", "Ese tablero tiene una partida en curso");
        if (req.whitePlayerId() != null && req.whitePlayerId().equals(req.blackPlayerId())) {
            throw ApiException.badRequest("SAME_PLAYER", "Un jugador no puede jugar contra sí mismo");
        }
        List<RoomBoard> all = boards.findByRoomIdOrderByBoardNo(id);
        for (Long playerId : new Long[] {req.whitePlayerId(), req.blackPlayerId()}) {
            if (playerId != null) release(id, playerId, all, boardNo);
        }
        board.setWhitePlayerId(req.whitePlayerId());
        board.setBlackPlayerId(req.blackPlayerId());
        return changed(room, me);
    }

    @Transactional
    public RoomView start(UserPrincipal me, long id, int boardNo) {
        Room room = ownedOpen(me, id);
        RoomBoard board = board(id, boardNo);
        if (!board.ready()) throw ApiException.conflict("BOARD_NOT_READY", "Asigna a los dos jugadores del tablero");
        if (inPlay(board)) throw ApiException.conflict("BOARD_IN_PLAY", "Ese tablero ya está jugando");
        startGame(room, board, me);
        return changed(room, me);
    }

    /** Inicia todos los tableros listos (dos jugadores y sin partida en curso). */
    @Transactional
    public RoomView startAll(UserPrincipal me, long id) {
        Room room = ownedOpen(me, id);
        List<RoomBoard> ready = boards.findByRoomIdOrderByBoardNo(id).stream()
                .filter(b -> b.ready() && !inPlay(b)).toList();
        if (ready.isEmpty()) throw ApiException.conflict("NOTHING_TO_START", "No hay tableros listos para empezar");
        ready.forEach(b -> startGame(room, b, me));
        return changed(room, me);
    }

    /** Revancha en un tablero terminado: los mismos dos jugadores con los colores invertidos. */
    @Transactional
    public RoomView rematch(UserPrincipal me, long id, int boardNo) {
        Room room = ownedOpen(me, id);
        RoomBoard board = board(id, boardNo);
        if (board.getGameId() == null || inPlay(board) || !board.ready()) {
            throw ApiException.conflict("NO_FINISHED_GAME", "La revancha es para un tablero cuya partida terminó");
        }
        Long white = board.getWhitePlayerId();
        board.setWhitePlayerId(board.getBlackPlayerId());
        board.setBlackPlayerId(white);
        startGame(room, board, me);
        return changed(room, me);
    }

    /** Cierra la sala: no entra nadie más ni empiezan partidas; las que están en curso terminan normalmente. */
    @Transactional
    public RoomView close(UserPrincipal me, long id) {
        Room room = ownedOpen(me, id);
        room.setStatus(RoomStatus.CLOSED);
        room.setClosedAt(clock.instant());
        log.info("Sala {} cerrada", id);
        return changed(room, me);
    }

    /** Una partida de la sala cambió (jugada, fin): la sala también. Lo llama {@code RoomLive} tras el commit. */
    @Transactional
    public void gameChanged(long roomId) {
        if (rooms.bumpVersion(roomId) > 0) notifier.changedAfterCommit(roomId);
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private void configure(Room room, RoomRequest req, long joined) {
        int maxPlayers = req.playersOrDefault();
        if (maxPlayers < joined) {
            throw ApiException.conflict("MAX_BELOW_MEMBERS", "Ya hay " + joined + " jugadores en la sala");
        }
        room.setName(req.name().trim());
        room.setMaxPlayers(maxPlayers);
        room.setInitialSeconds(req.minutes() * 60);
        room.setIncrementSeconds(req.incrementSeconds());
        room.setBoards(req.boards());
    }

    /** Agrega los tableros nuevos o quita los que sobran (solo si no tienen partida en curso). */
    private void resizeBoards(Room room) {
        List<RoomBoard> all = boards.findByRoomIdOrderByBoardNo(room.getId());
        for (RoomBoard b : all) {
            if (b.getBoardNo() <= room.getBoards()) continue;
            if (inPlay(b)) {
                throw ApiException.conflict("BOARD_IN_PLAY", "El tablero " + b.getBoardNo() + " tiene una partida en curso");
            }
            boards.delete(b); // sus jugadores pasan a mirar
        }
        for (int n = all.size() + 1; n <= room.getBoards(); n++) boards.save(new RoomBoard(room.getId(), n));
    }

    private void release(long roomId, long playerId, List<RoomBoard> all, int targetBoard) {
        if (!members.existsByRoomIdAndPlayerId(roomId, playerId)) {
            throw ApiException.badRequest("NOT_A_MEMBER", "Ese jugador no entró a la sala");
        }
        for (RoomBoard other : all) {
            if (other.getBoardNo() == targetBoard || !other.seats(playerId)) continue;
            if (inPlay(other)) {
                throw ApiException.conflict("PLAYER_IN_GAME", "Ese jugador está jugando en el tablero " + other.getBoardNo());
            }
            other.clearSeat(playerId);
        }
    }

    private void startGame(Room room, RoomBoard board, UserPrincipal me) {
        Game g = gameService.startRoomGame(me.playerId(), room.getId(), board.getBoardNo(), room.getInitialSeconds(),
                room.getIncrementSeconds(), board.getWhitePlayerId(), board.getBlackPlayerId());
        board.setGameId(g.getId());
    }

    private boolean inPlay(RoomBoard b) {
        return b.getGameId() != null
                && games.findById(b.getGameId()).map(g -> g.getStatus() == GameStatus.ACTIVE).orElse(false);
    }

    private RoomBoard board(long roomId, int boardNo) {
        return boards.findByRoomIdAndBoardNo(roomId, boardNo)
                .orElseThrow(() -> ApiException.notFound("BOARD_NOT_FOUND", "La sala no tiene el tablero " + boardNo));
    }

    private Room ownedOpen(UserPrincipal me, long id) {
        Room room = queries.require(id);
        if (!room.isOwnedBy(me.playerId())) throw notOrganizer();
        if (!room.isOpen()) throw ApiException.conflict("ROOM_CLOSED", "La sala está cerrada");
        return room;
    }

    private static ApiException notOrganizer() {
        return ApiException.forbidden("NOT_ROOM_ORGANIZER", "Solo el organizador de la sala puede hacer esto");
    }

    private String freeCode() {
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String code = codes.next();
            if (!rooms.existsByCodeAndStatus(code, RoomStatus.OPEN)) return code;
        }
        throw new IllegalStateException("No se encontró un código libre para la sala");
    }

    private RoomView changed(Room room, UserPrincipal me) {
        room.touch();
        rooms.saveAndFlush(room);
        notifier.changedAfterCommit(room.getId());
        return queries.view(me.playerId(), room.getId());
    }
}
