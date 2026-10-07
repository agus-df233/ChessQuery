package cl.chessquery.game.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCodeAndStatus(String code, RoomStatus status);

    boolean existsByCodeAndStatus(String code, RoomStatus status);

    long countByOrganizationIdAndStatus(Long organizationId, RoomStatus status);

    List<Room> findTop20ByOrganizerIdOrderByCreatedAtDesc(Long organizerId);

    @Query("select r from Room r where r.id in (select m.roomId from RoomMember m where m.playerId = :p) "
            + "order by r.createdAt desc")
    List<Room> findJoinedBy(@Param("p") Long playerId);

    /** Una partida de la sala cambió: la sala también (sin cargarla ni pisar otros cambios). */
    @Modifying
    @Query("update Room r set r.version = r.version + 1 where r.id = :id")
    int bumpVersion(@Param("id") Long id);
}
