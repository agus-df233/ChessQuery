package cl.chessquery.users.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubRepository extends JpaRepository<Club, Integer> {
    Optional<Club> findFirstByNameIgnoreCase(String name);
    List<Club> findAllByOrderByNameAsc();
}
