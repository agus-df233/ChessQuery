import { describe, expect, it } from 'vitest';
import type { RoundView } from '../api/tournamentTypes';
import { follow, followed, historyOf, outcomeFor, standingOf } from './follow';

const ref = (id: number, name: string) => ({ playerId: id, name, title: null, rating: 1500 });
const rounds: RoundView[] = [
  { number: 1, complete: true, boards: [
    { board: 1, white: ref(1, 'Ana'), black: ref(2, 'Luis'), result: 'BLACK_WINS', resultLabel: '0-1' },
    { board: 2, white: ref(3, 'Vicente'), black: null, result: 'BYE', resultLabel: 'bye' }] },
  { number: 2, complete: false, boards: [
    { board: 1, white: ref(2, 'Luis'), black: ref(3, 'Vicente'), result: null, resultLabel: null },
    { board: 2, white: ref(1, 'Ana'), black: null, result: 'BYE', resultLabel: 'bye' }] },
];

describe('seguir a un jugador', () => {
  it('cuenta cada ronda desde su lado, la más reciente primero', () => {
    expect(historyOf(rounds, 2)).toEqual([
      { round: 2, board: 1, color: 'blancas', rival: 'Vicente', outcome: 'en juego' },
      { round: 1, board: 1, color: 'negras', rival: 'Ana', outcome: 'ganó' },
    ]);
    expect(historyOf(rounds, 1)[0].outcome).toBe('descansa (bye)');
    expect(historyOf(rounds, 1)[1]).toMatchObject({ color: 'blancas', outcome: 'perdió' });
    expect(historyOf(rounds, 99)[0].outcome).toBe('no juega esta ronda');
  });

  it('traduce cada resultado según el color', () => {
    expect(outcomeFor('WHITE_FORFEIT_WIN', true)).toBe('ganó por no presentación');
    expect(outcomeFor('WHITE_FORFEIT_WIN', false)).toBe('perdió por no presentación');
    expect(outcomeFor('DRAW', false)).toBe('tablas');
    expect(outcomeFor('DOUBLE_FORFEIT', true)).toBe('no se presentó');
    expect(outcomeFor('BYE', true)).toBe('descansa (bye)');
  });

  it('busca su fila en la tabla y recuerda a quién sigo', () => {
    expect(standingOf([], 2)).toBeNull();
    expect(standingOf([{ position: 1, player: ref(2, 'Luis'), points: 1, buchholzCut1: 0, buchholz: 0, sonnebornBerger: 0, wins: 1, played: 1 }], 2)?.position).toBe(1);
    follow(5, 2);
    expect(followed(5)).toBe(2);
    follow(5, null);
    expect(followed(5)).toBeNull();
  });
});
