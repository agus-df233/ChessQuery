package cl.chessquery.tournament.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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
}
