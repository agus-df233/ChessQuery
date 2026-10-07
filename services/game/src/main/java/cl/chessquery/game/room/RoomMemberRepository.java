package cl.chessquery.game.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMember.Key> {

    List<RoomMember> findByRoomIdOrderByJoinedAt(Long roomId);

    long countByRoomId(Long roomId);

    boolean existsByRoomIdAndPlayerId(Long roomId, Long playerId);
}
