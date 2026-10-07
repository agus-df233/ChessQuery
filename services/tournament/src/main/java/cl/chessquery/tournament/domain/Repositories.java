package cl.chessquery.tournament.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repositorios del servicio (uno por tabla), juntos porque son solo firmas. */
public final class Repositories {

    private Repositories() {}

    public interface Tournaments extends JpaRepository<Tournament, Long> {
        @Modifying
        @Query("update Tournament t set t.version = t.version + 1 where t.id = :id")
        int bumpVersion(@Param("id") Long id);

        @Query("select t.version from Tournament t where t.id = :id")
        Optional<Long> findVersionById(@Param("id") Long id);

        List<Tournament> findByOrganizerIdOrderByStartDateDesc(Long organizerId);
        List<Tournament> findByStatusInOrderByStartDateAsc(Collection<Status> statuses);
        List<Tournament> findByIdInOrderByStartDateDesc(Collection<Long> ids);
        long countByOrganizationIdAndStatusIn(Long organizationId, Collection<Status> statuses);
    }

    public interface Registrations extends JpaRepository<Registration, Long> {
        List<Registration> findByTournamentIdOrderBySeedRatingDescIdAsc(Long tournamentId);
        Optional<Registration> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);
        List<Registration> findByPlayerId(Long playerId);
        Optional<Registration> findByCheckinCode(String checkinCode);
        long countByTournamentId(Long tournamentId);
    }

    public interface Rounds extends JpaRepository<Round, Long> {
        List<Round> findByTournamentIdOrderByNumberAsc(Long tournamentId);
        Optional<Round> findByTournamentIdAndNumber(Long tournamentId, int number);
    }

    public interface Pairings extends JpaRepository<Pairing, Long> {
        List<Pairing> findByRoundIdInOrderByBoardAsc(Collection<Long> roundIds);
        List<Pairing> findByRoundIdOrderByBoardAsc(Long roundId);
        Optional<Pairing> findByRoundIdAndBoard(Long roundId, int board);

        @Modifying
        @Query("update Pairing p set p.whitePlayerId = :to where p.whitePlayerId = :from")
        int reassignWhite(@Param("from") Long from, @Param("to") Long to);

        @Modifying
        @Query("update Pairing p set p.blackPlayerId = :to where p.blackPlayerId = :from")
        int reassignBlack(@Param("from") Long from, @Param("to") Long to);
    }

    public interface FederationTournaments extends JpaRepository<FederationTournament, String> {
        List<FederationTournament> findByStartDateGreaterThanEqualOrderByStartDateAsc(LocalDate from);
    }
}
