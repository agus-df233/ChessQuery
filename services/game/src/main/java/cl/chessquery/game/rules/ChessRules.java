package cl.chessquery.game.rules;

import cl.chessquery.game.domain.Outcome;
import cl.chessquery.game.domain.Termination;
import io.github.wolfraam.chessgame.ChessGame;
import io.github.wolfraam.chessgame.board.Piece;
import io.github.wolfraam.chessgame.board.PieceType;
import io.github.wolfraam.chessgame.board.Side;
import io.github.wolfraam.chessgame.board.Square;
import io.github.wolfraam.chessgame.move.IllegalMoveException;
import io.github.wolfraam.chessgame.move.Move;
import io.github.wolfraam.chessgame.notation.NotationType;
import io.github.wolfraam.chessgame.pgn.PGNExporter;
import io.github.wolfraam.chessgame.pgn.PGNTag;
import io.github.wolfraam.chessgame.result.ChessGameResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Reglas del ajedrez sobre la librería {@code chessgame} (MIT): el servidor reproduce las jugadas guardadas en UCI y
 * valida cada jugada nueva. Nunca se confía en el tablero que manda el cliente.
 */
public final class ChessRules {

    public static final String INITIAL_FEN = ChessGame.STANDARD_INITIAL_FEN;

    private ChessRules() {}

    /** Resultado de aplicar una jugada: SAN, nueva posición y, si terminó, cómo. */
    public record Played(String uci, String san, String fen, Outcome outcome, Termination termination) {
        public boolean ended() { return outcome != null; }
    }

    public static ChessGame replay(List<String> uciMoves) {
        ChessGame game = new ChessGame();
        uciMoves.forEach(m -> game.playMove(NotationType.UCI, m));
        return game;
    }

    /** Juega {@code uci} tras {@code previous}. Lanza {@link IllegalArgumentException} si es ilegal o mal escrita. */
    public static Played play(List<String> previous, String uci) {
        ChessGame game = replay(previous);
        String normalized = uci == null ? "" : uci.trim().toLowerCase();
        Move move;
        try {
            move = game.getMove(NotationType.UCI, normalized);
        } catch (IllegalMoveException | IllegalArgumentException | StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Jugada mal escrita: " + uci);
        }
        if (move == null || !game.isLegalMove(move)) throw new IllegalArgumentException("Jugada ilegal: " + uci);
        String san = game.getNotation(NotationType.SAN, move);
        game.playMove(move);
        ChessGameResult r = game.getGameResult();
        return new Played(normalized, san, game.getFen(), r == null ? null : outcome(r), r == null ? null : termination(r));
    }

    private static Outcome outcome(ChessGameResult r) {
        return switch (r.chessGameResultType) {
            case WHITE_WINS -> Outcome.WHITE_WINS;
            case BLACK_WINS -> Outcome.BLACK_WINS;
            case DRAW -> Outcome.DRAW;
        };
    }

    private static Termination termination(ChessGameResult r) {
        if (r.drawType == null) return Termination.CHECKMATE;
        return switch (r.drawType) {
            case STALE_MATE -> Termination.STALEMATE;
            case INSUFFICIENT_MATERIAL -> Termination.INSUFFICIENT_MATERIAL;
            case THREEFOLD_REPETITION -> Termination.THREEFOLD_REPETITION;
            case FIFTY_MOVE_RULE -> Termination.FIFTY_MOVE_RULE;
        };
    }

    /**
     * ¿Puede dar mate ese color con lo que le queda? Si no, cuando a su rival se le acaba el tiempo es tablas
     * (regla FIDE 6.9). Rey solo o rey con un alfil o un caballo no alcanzan.
     */
    public static boolean canMate(List<String> uciMoves, boolean white) {
        ChessGame game = replay(uciMoves);
        Side side = white ? Side.WHITE : Side.BLACK;
        int minors = 0;
        for (Square sq : game.getOccupiedSquares()) {
            Piece p = game.getPiece(sq);
            if (p.side != side || p.pieceType == PieceType.KING) continue;
            if (p.pieceType != PieceType.BISHOP && p.pieceType != PieceType.KNIGHT) return true;
            minors++;
        }
        return minors >= 2;
    }

    /** PGN con etiquetas estándar a partir de las jugadas y el resultado. */
    public static String pgn(List<String> uciMoves, Map<PGNTag, String> tags) {
        ChessGame game = replay(uciMoves);
        tags.forEach((tag, value) -> { if (value != null) game.getPGNData().setPGNTag(tag, value); });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PGNExporter(out).write(game);
        return out.toString(StandardCharsets.UTF_8);
    }
}
