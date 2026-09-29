package cl.chessquery.users.player;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    Optional<Player> findByRutHash(String rutHash);
    List<Player> findByRutIsNotNullAndRutHashIsNull();
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

    /**
     * Filas federadas sin dueño con el mismo nombre completo: candidatas a "¿eres tú?". Nunca se
     * fusionan solas (homónimos); las confirma el titular.
     */
    @Query(value = """
            SELECT p.* FROM users.player p
            WHERE lower(p.first_name || ' ' || p.last_name) = lower(:fullName)
              AND p.external_subject IS NULL AND p.provisional = FALSE AND p.active = TRUE
              AND p.id <> :excludeId
            ORDER BY p.id
            LIMIT 5
            """, nativeQuery = true)
    List<Player> findUnclaimedByFullName(@Param("fullName") String fullName, @Param("excludeId") Long excludeId);

    /**
     * Búsqueda difusa. El operador {@code %} de pg_trgm usa el índice GIN de la misma
     * expresión; el RUT se compara por su hash (nunca en claro) y el FIDE id exacto.
     */
    @Query(value = """
            SELECT p.* FROM users.player p
            WHERE  lower(p.first_name || ' ' || p.last_name) % lower(:q)
                OR p.rut_hash = :rutHash
                OR p.fide_id = :q
            ORDER BY similarity(lower(p.first_name || ' ' || p.last_name), lower(:q)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Player> searchFuzzy(@Param("q") String q, @Param("rutHash") String rutHash, @Param("limit") int limit);

    /**
     * Ranking por un tipo de rating (nacional o FIDE standard/rapid/blitz), filtrable por región
     * y rango de año de nacimiento (categoría). El tipo llega ya validado por RankingService.
     */
    @Query(value = """
            SELECT p.* FROM users.player p
            WHERE  (CASE CAST(:type AS text)
                        WHEN 'FIDE_STANDARD' THEN p.elo_fide_standard
                        WHEN 'FIDE_RAPID'    THEN p.elo_fide_rapid
                        WHEN 'FIDE_BLITZ'    THEN p.elo_fide_blitz
                        ELSE p.elo_national END) IS NOT NULL
              AND (CAST(:region AS text) IS NULL OR lower(p.region) = lower(CAST(:region AS text)))
              AND (CAST(:minYear AS integer) IS NULL OR p.birth_year >= CAST(:minYear AS integer))
              AND (CAST(:maxYear AS integer) IS NULL OR p.birth_year <= CAST(:maxYear AS integer))
            ORDER BY (CASE CAST(:type AS text)
                        WHEN 'FIDE_STANDARD' THEN p.elo_fide_standard
                        WHEN 'FIDE_RAPID'    THEN p.elo_fide_rapid
                        WHEN 'FIDE_BLITZ'    THEN p.elo_fide_blitz
                        ELSE p.elo_national END) DESC, p.last_name ASC
            """, nativeQuery = true)
    List<Player> findRanking(@Param("type") String type,
                             @Param("region") String region,
                             @Param("minYear") Integer minYear,
                             @Param("maxYear") Integer maxYear,
                             Pageable pageable);
}
