package cl.chessquery.tournament.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tournament")
@Getter
@Setter
@NoArgsConstructor
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizationId;
    private Long organizerId;
    private String name;
    private String city;
    private String region;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private Format format;

    private int roundsPlanned;
    private String timeControl;
    private boolean rated;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant finishedAt;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isOwnedBy(long playerId) {
        return organizerId != null && organizerId == playerId;
    }
}
