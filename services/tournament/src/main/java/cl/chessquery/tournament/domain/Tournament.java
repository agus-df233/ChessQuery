package cl.chessquery.tournament.domain;

import cl.chessquery.common.rating.TimeControlCategory;
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
    /** Etiqueta libre del ritmo (p. ej. "90+30"); el ritmo que cuenta es {@link #category()}. */
    private String timeControl;
    private Integer baseMinutes;
    private Integer incrementSeconds;
    private boolean rated;

    // Reglas de inscripción (null = sin límite)
    private Instant registrationClosesAt;
    private Integer maxPlayers;
    private boolean requiresApproval;
    private Integer minRating;
    private Integer maxRating;
    /** Quien no se acredita el día del torneo no juega la ronda 1. */
    private boolean checkinRequired;

    /**
     * Versión para el tiempo real: la sube {@code TournamentLive} con un UPDATE atómico. Solo lectura para Hibernate:
     * si la escribiera al guardar el torneo, pisaría el aumento con el valor que tenía al cargarlo.
     */
    @Column(insertable = false, updatable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant finishedAt;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /**
     * Ritmo del torneo: define qué ELO ChessQuery se siembra y se actualiza al cerrar. Sin ritmo estructurado (torneos
     * antiguos con una etiqueta que no se pudo leer) cuenta como rápido, el ritmo habitual de los torneos de club.
     */
    public TimeControlCategory category() {
        if (baseMinutes == null) return TimeControlCategory.RAPID;
        return TimeControlCategory.of(baseMinutes * 60, incrementSeconds == null ? 0 : incrementSeconds);
    }

    public boolean isOwnedBy(long playerId) {
        return organizerId != null && organizerId == playerId;
    }
}
