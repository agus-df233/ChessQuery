import { useState } from 'react';
import { Button } from '@chessquery/ui-lib';
import type { RoomBoard } from '../../api/roomTypes';
import { ChessClock } from '../game/ChessClock';
import { piecesFromFen } from '../game/PlayBoard';
import { resultFor } from '../game/labels';

/** Máximo de tableros por pantalla (2×2): se leen bien en un proyector o una TV. */
export const BOARDS_PER_PAGE = 4;

const FILES = 'abcdefgh';
const GLYPH: Record<string, string> = { K: '♔', Q: '♕', R: '♖', B: '♗', N: '♘', P: '♙', k: '♚', q: '♛', r: '♜', b: '♝', n: '♞', p: '♟' };
const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

/** Páginas de tableros: [[1,2,3,4],[5,6,7,8],…]. */
export const pageOf = <T,>(items: T[], page: number) => items.slice(page * BOARDS_PER_PAGE, (page + 1) * BOARDS_PER_PAGE);
export const pageCount = (items: unknown[]) => Math.max(1, Math.ceil(items.length / BOARDS_PER_PAGE));

/** Estado del tablero en palabras (también es el nombre accesible de la miniatura). */
export const boardStatus = (b: RoomBoard) => {
  const g = b.game;
  if (!b.white || !b.black) return 'Esperando jugadores';
  if (!g) return 'Listo para empezar';
  if (g.status === 'ACTIVE') return `En juego · jugada ${Math.floor(g.ply / 2) + 1} · mueven ${g.sideToMove === 'WHITE' ? 'blancas' : 'negras'}`;
  return `Terminada · ${resultFor(g.result)}${g.terminationLabel ? ` por ${g.terminationLabel}` : ''}`;
};

/** Tablero en miniatura, solo para mirar: una imagen con nombre accesible (no 64 botones por tablero). */
const MiniBoard = ({ board }: { board: RoomBoard }) => {
  const pieces = piecesFromFen(board.game?.fen ?? INITIAL_FEN);
  const last = board.game?.moves.at(-1);
  const label = `Tablero ${board.boardNo}: ${board.white?.name ?? 'puesto libre'} (blancas) contra ${board.black?.name ?? 'puesto libre'} (negras), ${boardStatus(board).toLowerCase()}`;
  return (
    <div className="cq-mini-board" role="img" aria-label={label}>
      {[8, 7, 6, 5, 4, 3, 2, 1].map((rank) => [...FILES].map((file) => {
        const square = `${file}${rank}`;
        const piece = pieces[square];
        const dark = (FILES.indexOf(file) + rank) % 2 === 0;
        const highlight = last && (last.startsWith(square) || last.slice(2, 4) === square);
        return (
          <span key={square} className={`cq-mini-sq ${dark ? 'cq-sq-dark' : 'cq-sq-light'}${highlight ? ' cq-sq-last' : ''}`}>
            {piece && <span className={piece === piece.toUpperCase() ? 'cq-piece-white' : 'cq-piece-black'}>{GLYPH[piece]}</span>}
          </span>
        );
      }))}
    </div>
  );
};

const Side = ({ name, ms, running, color }: { name?: string; ms?: number; running: boolean; color: string }) => (
  <div className="cq-mini-side">
    <span>{color === 'blancas' ? '♔' : '♚'} {name ?? <em className="cq-muted">puesto libre</em>}</span>
    {ms !== undefined && <ChessClock ms={ms} running={running} label={`${name ?? color}`} />}
  </div>
);

/** Tiempo y si corre el reloj de un color (sin partida, no hay reloj). */
const clock = (board: RoomBoard, side: 'WHITE' | 'BLACK') => {
  const g = board.game;
  if (!g) return { ms: undefined, running: false };
  return { ms: side === 'WHITE' ? g.white.clockMs : g.black.clockMs, running: g.status === 'ACTIVE' && g.sideToMove === side };
};

const BoardCard = ({ board, actions }: { board: RoomBoard; actions?: (b: RoomBoard) => React.ReactNode }) => (
  <article className="cq-room-board" aria-label={`Tablero ${board.boardNo}`}>
    <header className="cq-room-board-head"><strong>Tablero {board.boardNo}</strong><span className="cq-muted">{boardStatus(board)}</span></header>
    <Side name={board.black?.name} {...clock(board, 'BLACK')} color="negras" />
    <MiniBoard board={board} />
    <Side name={board.white?.name} {...clock(board, 'WHITE')} color="blancas" />
    {actions && <div className="cq-actions">{actions(board)}</div>}
  </article>
);

/**
 * Cuadrícula de tableros de una sala, de a 4 por pantalla con paginación. `actions` agrega los botones del
 * organizador bajo cada tablero (iniciar, revancha); los espectadores solo miran.
 */
export const BoardGrid = ({ boards, actions }: { boards: RoomBoard[]; actions?: (b: RoomBoard) => React.ReactNode }) => {
  const [page, setPage] = useState(0);
  const pages = pageCount(boards);
  const current = Math.min(page, pages - 1);
  return (
    <section aria-label="Tableros de la sala">
      <div className="cq-room-grid">
        {pageOf(boards, current).map((b) => <BoardCard key={b.boardNo} board={b} actions={actions} />)}
      </div>
      {pages > 1 && (
        <nav className="cq-actions" aria-label="Páginas de tableros" style={{ justifyContent: 'center', marginTop: 12 }}>
          <Button size="sm" variant="secondary" disabled={current === 0} onClick={() => setPage(current - 1)}>← Anteriores</Button>
          <span aria-live="polite">Tableros {current * BOARDS_PER_PAGE + 1}–{Math.min(boards.length, (current + 1) * BOARDS_PER_PAGE)} de {boards.length}</span>
          <Button size="sm" variant="secondary" disabled={current === pages - 1} onClick={() => setPage(current + 1)}>Siguientes →</Button>
        </nav>
      )}
    </section>
  );
};
