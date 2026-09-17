package cl.chessquery.users.organization;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Club del organizador como tenant. Ser dueño de una organización ES el rol ORGANIZER:
 * la librería de auth lo resuelve consultando esta tabla. El plan lo cambia solo billing.
 */
@Entity
@Table(name = "organization")
@Getter @Setter
@NoArgsConstructor
public class Organization {

    /** Límites por plan; tunables en código, el pago cambia el plan. */
    public enum Plan {
        FREE(50, 3), PRO(1000, 100);

        public final int maxRosterPlayers;
        public final int maxActiveTournaments;

        Plan(int maxRosterPlayers, int maxActiveTournaments) {
            this.maxRosterPlayers = maxRosterPlayers;
            this.maxActiveTournaments = maxActiveTournaments;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_player_id", nullable = false, unique = true)
    private Long ownerPlayerId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 120)
    private String city;

    @Column(length = 500)
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Organization(Long ownerPlayerId, String name) {
        this.ownerPlayerId = ownerPlayerId;
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
