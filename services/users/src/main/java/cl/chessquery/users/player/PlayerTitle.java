package cl.chessquery.users.player;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Título FIDE/nacional del jugador. La fila con {@code current} es el título vigente. */
@Entity
@Table(name = "player_title_history")
@Getter
@NoArgsConstructor
public class PlayerTitle {

    public enum Title { GM, IM, FM, CM, WGM, WIM, WFM, WCM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Title title;

    @Column(name = "title_date", nullable = false)
    private LocalDate titleDate;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(length = 50)
    private String source;

    /** Título vigente nuevo (el anterior se cierra con {@link #close()}). */
    public static PlayerTitle current(Long playerId, Title title, LocalDate since, String source) {
        PlayerTitle t = new PlayerTitle();
        t.playerId = playerId;
        t.title = title;
        t.titleDate = since;
        t.current = true;
        t.source = source;
        return t;
    }

    public void close() {
        this.current = false;
    }
}
