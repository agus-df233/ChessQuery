package cl.chessquery.game.live;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WsConnectionRepository extends JpaRepository<WsConnection, String> {

    List<WsConnection> findByGameId(Long gameId);

    @Modifying
    @Query("delete from WsConnection c where c.lastSeenAt < :before")
    int deleteSeenBefore(@Param("before") Instant before);
}
