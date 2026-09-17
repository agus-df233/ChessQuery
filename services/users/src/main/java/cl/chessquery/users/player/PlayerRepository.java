package cl.chessquery.users.player;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@code player}. Las consultas nativas califican el schema ({@code users.player})
 * porque Hibernate no reescribe SQL nativo con {@code default_schema}.
 */
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByExternalSubject(String externalSubject);
    Optional<Player> findByEmail(String email);
    Optional<Player> findByRut(String rut);
    Optional<Player> findByFideId(String fideId);
    Optional<Player> findByFederationId(String federationId);
    Optional<Player> findByLichessUsernameIgnoreCase(String username);
    Optional<Player> findByChesscomUsernameIgnoreCase(String username);

    /** Roster provisorio de un organizador (activos e inactivos, ordenado por apellido). */
    List<Player> findByCreatedByOrganizerIdAndProvisionalTrueOrderByLastNameAscFirstNameAsc(Long organizerId);

    /** Tamaño del roster activo: lo que limita el plan. */
    long countByCreatedByOrganizerIdAndProvisionalTrueAndActiveTrue(Long organizerId);

    /** Usernames vinculados, para que el ETL sincronice ratings en lote. */
    @Query("select p.lichessUsername from Player p where p.lichessUsername is not null")
    List<String> findAllLichessUsernames();

    @Query("select p.chesscomUsername from Player p where p.chesscomUsername is not null")
    List<String> findAllChesscomUsernames();

    /** Match exacto por nombre completo (último recurso del enriquecimiento AJEFECH). */
    @Query(value = """
            SELECT p.* FROM users.player p
            WHERE lower(p.first_name || ' ' || p.last_name) = lower(:fullName)
            LIMIT 1
            """, nativeQuery = true)
    Optional<Player> findByFullNameIgnoreCase(@Param("fullName") String fullName);

    /**
     * Búsqueda difusa. El operador {@code %} de pg_trgm usa el índice GIN de la misma
     * expresión; RUT y FIDE id se comparan exactos. Ordena por similaridad.
     */
    @Query(value = """
            SELECT p.* FROM users.player p
            WHERE  lower(p.first_name || ' ' || p.last_name) % lower(:q)
                OR p.rut = :q
                OR p.fide_id = :q
            ORDER BY similarity(lower(p.first_name || ' ' || p.last_name), lower(:q)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Player> searchFuzzy(@Param("q") String q, @Param("limit") int limit);

    /** Ranking por ELO nacional, filtrable por región y rango de nacimiento (categoría de edad). */
    @Query(value = """
            SELECT p.* FROM users.player p
            WHERE  p.elo_national IS NOT NULL
              AND (CAST(:region AS text) IS NULL OR lower(p.region) = lower(CAST(:region AS text)))
              AND (CAST(:minBirth AS date) IS NULL OR p.birth_date >= CAST(:minBirth AS date))
              AND (CAST(:maxBirth AS date) IS NULL OR p.birth_date <= CAST(:maxBirth AS date))
            ORDER BY p.elo_national DESC, p.last_name ASC
            """, nativeQuery = true)
    List<Player> findRanking(@Param("region") String region,
                             @Param("minBirth") LocalDate minBirth,
                             @Param("maxBirth") LocalDate maxBirth,
                             Pageable pageable);
}
