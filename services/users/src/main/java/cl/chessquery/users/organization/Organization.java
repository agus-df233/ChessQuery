package cl.chessquery.users.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Tenant del organizador (ADR-0005 v2). Ser dueño de una organización = rol ORGANIZER. */
@Entity
@Table(name = "organization")
public class Organization {

    public enum Plan { FREE, PRO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_player_id", nullable = false, unique = true)
    private Long ownerPlayerId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Organization() {}

    public Organization(Long ownerPlayerId, String name) {
        this.ownerPlayerId = ownerPlayerId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getOwnerPlayerId() { return ownerPlayerId; }
    public String getName() { return name; }
    public Plan getPlan() { return plan; }
    public Instant getCreatedAt() { return createdAt; }
}
