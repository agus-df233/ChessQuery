package cl.chessquery.users.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** País del catálogo (ISO 3166-1 alpha-3 + federación FIDE). Solo lectura desde la API. */
@Entity
@Table(name = "country")
@Getter
@NoArgsConstructor
public class Country {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "iso_code", nullable = false, unique = true, length = 3)
    private String isoCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "fide_federation", length = 10)
    private String fideFederation;

    public record Dto(Integer id, String isoCode, String name, String fideFederation) {
        public static Dto of(Country c) {
            return c == null ? null : new Dto(c.id, c.isoCode, c.name, c.fideFederation);
        }
    }
}
