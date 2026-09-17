import { useMemo } from 'react';

export interface ChessBoardProps {
  /** FEN string. Only the piece-placement field is required; the rest is ignored. */
  fen?: string;
  size?: number;
  showCoords?: boolean;
}

const INITIAL_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR';
const PIECE_MAP: Record<string, string> = {
  K: '♔', Q: '♕', R: '♖', B: '♗', N: '♘', P: '♙',
  k: '♚', q: '♛', r: '♜', b: '♝', n: '♞', p: '♟',
};

function fenToBoard(fen: string): (string | null)[][] {
  const placement = fen.split(' ')[0] ?? INITIAL_FEN;
  const rows = placement.split('/');
  return rows.map((row) => {
    const squares: (string | null)[] = [];
    for (const ch of row) {
      if (/\d/.test(ch)) {
        const n = parseInt(ch, 10);
        for (let i = 0; i < n; i++) squares.push(null);
      } else {
        squares.push(PIECE_MAP[ch] ?? null);
      }
    }
    while (squares.length < 8) squares.push(null);
    return squares;
  });
}

const FILES = 'abcdefgh';

export const ChessBoard = ({ fen = INITIAL_FEN, size = 320, showCoords = true }: ChessBoardProps) => {
  const board = useMemo(() => fenToBoard(fen), [fen]);
  const sq = Math.floor(size / 8);

  return (
    <div style={{ display: 'inline-flex', gap: 0 }}>
      {showCoords && (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {[8, 7, 6, 5, 4, 3, 2, 1].map((n) => (
            <div
              key={n}
              style={{
                width: 18,
                height: sq,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 10,
                color: 'var(--text-dim)',
                fontFamily: "'Space Grotesk', system-ui, sans-serif",
              }}
            >
              {n}
            </div>
          ))}
        </div>
      )}
      <div>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: `repeat(8, ${sq}px)`,
            border: '2px solid var(--border-hi)',
            borderRadius: 4,
            overflow: 'hidden',
          }}
        >
          {board.map((row, rank) =>
            row.map((piece, file) => {
              const light = (rank + file) % 2 === 0;
              return (
                <div
                  key={`${rank}-${file}`}
                  style={{
                    width: sq,
                    height: sq,
                    background: light ? '#e9edf2' : '#7c8ba1',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: Math.floor(sq * 0.7),
                    userSelect: 'none',
                    lineHeight: 1,
                  }}
                >
                  {piece && (
                    <span style={{ textShadow: '0 1px 3px rgba(0,0,0,0.35)' }}>{piece}</span>
                  )}
                </div>
              );
            }),
          )}
        </div>
        {showCoords && (
          <div style={{ display: 'flex' }}>
            {FILES.split('').map((f) => (
              <div
                key={f}
                style={{
                  width: sq,
                  textAlign: 'center',
                  fontSize: 10,
                  color: 'var(--text-dim)',
                  paddingTop: 4,
                  fontFamily: "'Space Grotesk', system-ui, sans-serif",
                }}
              >
                {f}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
