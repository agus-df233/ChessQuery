package cl.chessquery.tournament.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Torneo del calendario de la Federación (lo trae el ETL). Solo datos del evento, sin participantes. */
@Entity
@Table(name = "federation_tournament")
@Getter
@Setter
@NoArgsConstructor
public class FederationTournament {

    @Id
    private String federationTournamentId;

    private String title;
    private String city;
    private String region;
    private String clubName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String type;
    private Integer rounds;
    private String timeControl;
    private String category;
    private boolean ratedNational;
    private boolean ratedFide;
    private Instant updatedAt = Instant.now();
}
