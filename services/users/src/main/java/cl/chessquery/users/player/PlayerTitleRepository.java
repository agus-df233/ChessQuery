package cl.chessquery.users.player;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface PlayerTitleRepository extends JpaRepository<PlayerTitle, Long> {

    Optional<PlayerTitle> findFirstByPlayerIdAndCurrentTrue(Long playerId);

    /** Títulos vigentes de varios jugadores en una sola consulta (evita N+1 en listas). */
    @Query("select t from PlayerTitle t where t.current = true and t.playerId in :ids")
    List<PlayerTitle> findCurrentByPlayerIds(@Param("ids") Collection<Long> ids);

    default Map<Long, String> currentTitlesOf(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return findCurrentByPlayerIds(ids).stream()
                .collect(Collectors.toMap(PlayerTitle::getPlayerId, t -> t.getTitle().name(), (a, b) -> a));
    }

    default String currentTitleOf(Long id) {
        return findFirstByPlayerIdAndCurrentTrue(id).map(t -> t.getTitle().name()).orElse(null);
    }

    /** Traspasa los títulos de una ficha federada a la cuenta que la reclamó. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update PlayerTitle t set t.playerId = :to where t.playerId = :from")
    int reassign(@Param("from") Long from, @Param("to") Long to);
}
