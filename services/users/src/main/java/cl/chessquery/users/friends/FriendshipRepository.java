package cl.chessquery.users.friends;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** La fila del par, sin importar quién pidió primero (hay a lo más una). */
    @Query("""
            select f from Friendship f
            where (f.requesterId = :a and f.addresseeId = :b) or (f.requesterId = :b and f.addresseeId = :a)
            """)
    Optional<Friendship> findByPair(@Param("a") Long a, @Param("b") Long b);

    @Query("""
            select f from Friendship f
            where (f.requesterId = :playerId or f.addresseeId = :playerId) and f.status = :status
            order by f.respondedAt desc, f.createdAt desc
            """)
    List<Friendship> findByPlayerAndStatus(@Param("playerId") Long playerId, @Param("status") Friendship.Status status);

    List<Friendship> findByAddresseeIdAndStatusOrderByCreatedAtDesc(Long addresseeId, Friendship.Status status);

    List<Friendship> findByRequesterIdAndStatusOrderByCreatedAtDesc(Long requesterId, Friendship.Status status);
}
