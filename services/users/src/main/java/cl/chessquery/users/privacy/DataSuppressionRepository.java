package cl.chessquery.users.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Objects;

public interface DataSuppressionRepository extends JpaRepository<DataSuppression, String> {

    /** true si alguno de los hashes (se ignoran los null) está suprimido. */
    default boolean anySuppressed(Collection<String> hashes) {
        var present = hashes.stream().filter(Objects::nonNull).toList();
        return !present.isEmpty() && existsByIdentifierHashIn(present);
    }

    boolean existsByIdentifierHashIn(Collection<String> hashes);
}
