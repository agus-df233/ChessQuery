package cl.chessquery.users.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Identificador (hasheado) que nunca debe volver a importarse: supresión u oposición del titular. */
@Entity
@Table(name = "data_suppression")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataSuppression {

    public enum Reason { ERASURE, OBJECTION }

    @Id
    @Column(name = "identifier_hash", length = 64)
    private String identifierHash;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DataSuppression(String identifierHash, Reason reason) {
        this.identifierHash = identifierHash;
        this.reason = reason.name();
        this.createdAt = Instant.now();
    }
}
