package cl.chessquery.game.rules;

import cl.chessquery.game.domain.Outcome;
import cl.chessquery.game.domain.Termination;
import io.github.wolfraam.chessgame.pgn.PGNTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChessRulesTest {

    /** Mate del pastor: 1.e4 e5 2.Ac4 Cc6 3.Dh5 Cf6 4.Dxf7#. */
    static final List<String> SCHOLARS = List.of("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6");

    @Test
    void jugadaLegalDevuelveSanYPosicion() {
        ChessRules.Played p = ChessRules.play(List.of(), "E2E4");
        assertThat(p.uci()).isEqualTo("e2e4");
        assertThat(p.san()).isEqualTo("e4");
        assertThat(p.fen()).startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b");
        assertThat(p.ended()).isFalse();
    }

    @Test
    void jugadasIlegalesOMalEscritasSeRechazan() {
        assertThatThrownBy(() -> ChessRules.play(List.of(), "e2e5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChessRules.play(List.of(), "e7e5")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChessRules.play(List.of(), "zz")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChessRules.play(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jaqueMate() {
        ChessRules.Played p = ChessRules.play(SCHOLARS, "h5f7");
        assertThat(p.san()).isEqualTo("Qxf7#");
        assertThat(p.outcome()).isEqualTo(Outcome.WHITE_WINS);
        assertThat(p.termination()).isEqualTo(Termination.CHECKMATE);
    }

    @Test
    void ahogadoEsTablas() {
        // Posición conocida de ahogado rápido (Sam Loyd, 10 jugadas)
        List<String> moves = List.of("e2e3", "a7a5", "d1h5", "a8a6", "h5a5", "h7h5", "h2h4", "a6h6", "a5c7", "f7f6",
                "c7d7", "e8f7", "d7b7", "d8d3", "b7b8", "d3h7", "b8c8", "f7g6");
        ChessRules.Played p = ChessRules.play(moves, "c8e6");
        assertThat(p.outcome()).isEqualTo(Outcome.DRAW);
        assertThat(p.termination()).isEqualTo(Termination.STALEMATE);
    }

    @Test
    void puedeDarMateSegunMaterial() {
        assertThat(ChessRules.canMate(List.of(), true)).isTrue();
        assertThat(ChessRules.canMate(List.of(), false)).isTrue();
    }

    @Test
    void pgnConEtiquetasYResultado() {
        List<String> moves = new java.util.ArrayList<>(SCHOLARS);
        moves.add("h5f7");
        String pgn = ChessRules.pgn(moves, Map.of(PGNTag.WHITE, "Ana Soto", PGNTag.BLACK, "Luis Paz", PGNTag.RESULT, "1-0"));
        assertThat(pgn).contains("[White \"Ana Soto\"]").contains("[Black \"Luis Paz\"]").contains("Qxf7#").contains("1-0");
    }

    @Test
    void puntajes() {
        assertThat(Outcome.WHITE_WINS.scoreFor(true)).isEqualTo(1);
        assertThat(Outcome.WHITE_WINS.scoreFor(false)).isEqualTo(0);
        assertThat(Outcome.DRAW.scoreFor(false)).isEqualTo(0.5);
        assertThat(Outcome.winner(false)).isEqualTo(Outcome.BLACK_WINS);
    }
}
