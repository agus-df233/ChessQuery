package cl.chessquery.users.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Club federativo del catálogo (el que figura en AJEFECH). No confundir con
 * {@code organization}, que es el club del organizador dentro de la plataforma.
 * Se crean desde el catálogo o al enriquecer jugadores desde AJEFECH (find-or-create).
 */
@Entity
@Table(name = "club")
@Getter
@NoArgsConstructor
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @Column(length = 100)
    private String city;

    @Column(name = "federation_code", length = 20)
    private String federationCode;

    public Club(String name) {
        this.name = name;
    }

    public record Dto(Integer id, String name, String city, String federationCode) {
        public static Dto of(Club c) {
            return c == null ? null : new Dto(c.id, c.name, c.city, c.federationCode);
        }
    }
}
