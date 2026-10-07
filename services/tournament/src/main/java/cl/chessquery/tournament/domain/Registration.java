package cl.chessquery.tournament.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/** Inscripción con una foto del nombre público y del rating al momento de inscribirse. */
@Entity
@Table(name = "registration")
@Getter
@Setter
@NoArgsConstructor
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tournamentId;
    private Long playerId;
    private String firstName;
    private String lastName;
    private String title;
    private String clubName;
    private int seedRating;
    private Integer platformRating;
    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private RegistrationStatus status = RegistrationStatus.CONFIRMED;

    /** Lo que lleva el QR de acreditación: aleatorio, sin datos personales. */
    private String checkinCode = newCheckinCode();
    private Instant checkedInAt;
    /** Primera ronda que ya no juega (retiro); 1 = no se presentó. */
    private Integer withdrawnFromRound;

    private static final SecureRandom RANDOM = new SecureRandom();

    static String newCheckinCode() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Cuenta para el cupo: confirmada o esperando aprobación. */
    public boolean holdsSeat() {
        return status == RegistrationStatus.CONFIRMED || status == RegistrationStatus.PENDING;
    }

    /** Participó del torneo: confirmada, o retirada después de haber jugado al menos una ronda. */
    public boolean participates() {
        return status == RegistrationStatus.CONFIRMED
                || (status == RegistrationStatus.WITHDRAWN && withdrawnFromRound != null && withdrawnFromRound > 1);
    }

    /** Se empareja en la ronda {@code round}: participa y no se retiró antes de esa ronda. */
    public boolean playsRound(int round) {
        return participates() && (withdrawnFromRound == null || round < withdrawnFromRound);
    }
}
