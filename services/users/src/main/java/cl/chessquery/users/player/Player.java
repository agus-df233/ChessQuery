package cl.chessquery.users.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "player")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_subject")
    private String externalSubject;

    @Column(name = "email")
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "provisional", nullable = false)
    private boolean provisional;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Player() {}

    public static Player fromIdentity(String subject, String email, String firstName, String lastName, String displayName) {
        Player p = new Player();
        p.externalSubject = subject;
        p.email = email;
        p.firstName = firstName == null || firstName.isBlank() ? "Jugador" : firstName;
        p.lastName = lastName == null ? "" : lastName;
        p.displayName = displayName;
        p.provisional = false;
        return p;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getExternalSubject() { return externalSubject; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getDisplayName() { return displayName; }
    public boolean isProvisional() { return provisional; }
    public Instant getCreatedAt() { return createdAt; }
}
