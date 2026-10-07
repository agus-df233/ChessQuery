package cl.chessquery.game.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomBoardRepository extends JpaRepository<RoomBoard, RoomBoard.Key> {

    List<RoomBoard> findByRoomIdOrderByBoardNo(Long roomId);

    Optional<RoomBoard> findByRoomIdAndBoardNo(Long roomId, Integer boardNo);
}
