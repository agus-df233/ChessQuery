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

    /** Traspasa el historial de una ficha federada a la cuenta que la reclamó. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update RatingHistory h set h.playerId = :to where h.playerId = :from")
    int reassign(@org.springframework.data.repository.query.Param("from") Long from,
                 @org.springframework.data.repository.query.Param("to") Long to);
}
