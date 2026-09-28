package cl.chessquery.users.rating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    /** Serie ascendente desde una fecha (para graficar). */
    List<RatingHistory> findByPlayerIdAndRatingTypeAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
            Long playerId, RatingType type, Instant since);

    /** Historial completo, todas las modalidades (exportación de datos del titular). */
    List<RatingHistory> findByPlayerIdOrderByRecordedAtAsc(Long playerId);
}
