import { useMemo, useState } from 'react';

const FILES = 'abcdefgh';
const GLYPH: Record<string, string> = { K: '♔', Q: '♕', R: '♖', B: '♗', N: '♘', P: '♙', k: '♚', q: '♛', r: '♜', b: '♝', n: '♞', p: '♟' };
const NAME: Record<string, string> = { k: 'rey', q: 'dama', r: 'torre', b: 'alfil', n: 'caballo', p: 'peón' };
const FEMININE = new Set(['q', 'r']);

/** Piezas por casilla ("e4" → "P") a partir del campo de posiciones del FEN. */
const PROMOTIONS: [value: string, label: string, white: string, black: string][] = [
  ['q', 'dama', '♕', '♛'], ['r', 'torre', '♖', '♜'], ['b', 'alfil', '♗', '♝'], ['n', 'caballo', '♘', '♞'],
];

/** A qué pieza corona un peón: cuatro botones grandes (táctiles), el elegido con aria-pressed. */
const PromotionPicker = ({ color, value, onChange }: { color: 'WHITE' | 'BLACK'; value: string; onChange: (v: string) => void }) => (
  <div className="cq-promotion" role="group" aria-label="Coronar a">
    <span className="cq-muted">Coronar a</span>
    {PROMOTIONS.map(([v, label, white, black]) => (
      <button key={v} type="button" aria-pressed={value === v} aria-label={label} title={label} onClick={() => onChange(v)}>
        <span aria-hidden="true">{color === 'WHITE' ? white : black}</span>
      </button>
    ))}
  </div>
);

export const piecesFromFen = (fen: string): Record<string, string> => {
  const pieces: Record<string, string> = {};
  fen.split(' ')[0].split('/').forEach((row, i) => {
    let file = 0;
    for (const ch of row) {
      if (/\d/.test(ch)) { file += Number(ch); continue; }
      pieces[`${FILES[file]}${8 - i}`] = ch;
      file++;
    }
  });
  return pieces;
};

const isWhitePiece = (p: string) => p === p.toUpperCase();

/** Un peón que llega a la última fila corona (por defecto a dama). */
const needsPromotion = (piece: string | undefined, to: string) =>
  (piece === 'P' && to.endsWith('8')) || (piece === 'p' && to.endsWith('1'));

/** "e4, peón blanco", "d8, dama negra", "e5, vacía" (concordancia de género en español). */
export const squareLabel = (square: string, piece?: string) => {
  if (!piece) return `${square}, vacía`;
  const kind = piece.toLowerCase();
  const color = isWhitePiece(piece) ? 'blanc' : 'negr';
  return `${square}, ${NAME[kind]} ${color}${FEMININE.has(kind) ? 'a' : 'o'}`;
};

export interface PlayBoardProps {
  fen: string;
  /** Mi color: orienta el tablero y limita qué piezas puedo tomar. null = espectador (solo mirar). */
  myColor: 'WHITE' | 'BLACK' | null;
  /** Solo se puede mover cuando es mi turno y la partida está en juego. */
  canMove: boolean;
  /** Mostrar la elección de pieza al coronar (solo mientras la partida está en juego). */
  showPromotion?: boolean;
  lastMove?: string;
  onMove: (uci: string) => void;
}

/**
 * Tablero jugable: clic (o Enter) en una pieza propia y luego en la casilla destino. Cada casilla es un botón con
 * nombre accesible ("e4, peón blanco"). Las reglas las valida el servidor: una jugada ilegal vuelve como error.
 */
export const PlayBoard = ({ fen, myColor, canMove, showPromotion = false, lastMove, onMove }: PlayBoardProps) => {
  const pieces = useMemo(() => piecesFromFen(fen), [fen]);
  const [from, setFrom] = useState<string | null>(null);
  const [promotion, setPromotion] = useState('q');
  const ranks = myColor === 'BLACK' ? [1, 2, 3, 4, 5, 6, 7, 8] : [8, 7, 6, 5, 4, 3, 2, 1];
  const files = myColor === 'BLACK' ? [...FILES].reverse() : [...FILES];
  const mine = (p?: string) => !!p && (myColor === 'WHITE' ? isWhitePiece(p) : !isWhitePiece(p));

  const click = (square: string) => {
    if (!canMove) return;
    if (mine(pieces[square])) { setFrom(square === from ? null : square); return; }
    if (!from) return;
    onMove(`${from}${square}${needsPromotion(pieces[from], square) ? promotion : ''}`);
    setFrom(null);
  };

  return (
    <div className="cq-playboard-wrap">
      <div className="cq-playboard" role="group" aria-label="Tablero">
        {ranks.map((rank) => (
          <div key={rank} className="cq-playboard-row">
            {files.map((file) => {
              const square = `${file}${rank}`;
              const piece = pieces[square];
              const dark = (FILES.indexOf(file) + rank) % 2 === 0;
              const highlight = lastMove && (lastMove.startsWith(square) || lastMove.slice(2, 4) === square);
              return (
                <button type="button" key={square} aria-label={squareLabel(square, piece)}
                        aria-pressed={from === square} disabled={!canMove}
                        className={`cq-sq ${dark ? 'cq-sq-dark' : 'cq-sq-light'}${highlight ? ' cq-sq-last' : ''}${from === square ? ' cq-sq-selected' : ''}`}
                        onClick={() => click(square)}>
                  <span aria-hidden="true" className={piece && isWhitePiece(piece) ? 'cq-piece-white' : 'cq-piece-black'}>
                    {piece ? GLYPH[piece] : ''}
                  </span>
                </button>
              );
            })}
          </div>
        ))}
      </div>
      {myColor && showPromotion && (
        <PromotionPicker color={myColor} value={promotion} onChange={setPromotion} />
      )}
    </div>
  );
};
