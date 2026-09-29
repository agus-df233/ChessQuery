package cl.chessquery.game.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {

    @Query("select g from Game g where (g.whitePlayerId = :p or g.blackPlayerId = :p) and g.status in :statuses "
            + "order by g.createdAt desc")
    List<Game> findByPlayerAndStatus(@Param("p") Long playerId, @Param("statuses") Collection<GameStatus> statuses);

    List<Game> findByStatus(GameStatus status);

    List<Game> findByStatusAndCreatedAtBefore(GameStatus status, Instant before);

    List<Game> findTop20ByStatusOrderByFinishedAtDesc(GameStatus status);
}
