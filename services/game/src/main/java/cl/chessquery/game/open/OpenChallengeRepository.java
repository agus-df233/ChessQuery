package cl.chessquery.game.open;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpenChallengeRepository extends JpaRepository<OpenChallenge, Long> {

    Optional<OpenChallenge> findByToken(String token);

    List<OpenChallenge> findByChallengerIdAndStatusOrderByCreatedAtDesc(Long challengerId, OpenChallenge.Status status);
}
