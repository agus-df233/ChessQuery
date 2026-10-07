import { describe, expect, it } from 'vitest';
import { categoryOf, PRESETS, timeControlLabel } from './timeControl';

describe('ritmos de juego', () => {
  it('clasifica igual que TimeControlCategory de Java (mismos casos)', () => {
    const cases: [number, number, string][] = [
      [1, 0, 'BULLET'], [2, 1, 'BULLET'], [3, 0, 'BLITZ'], [3, 2, 'BLITZ'], [5, 3, 'BLITZ'],
      [10, 0, 'RAPID'], [15, 10, 'RAPID'], [25, 0, 'CLASSICAL'], [90, 30, 'CLASSICAL'],
    ];
    for (const [m, i, cat] of cases) expect(categoryOf(m, i), `${m}+${i}`).toBe(cat);
  });

  it('respeta los cortes exactos (180, 480 y 1500 s estimados)', () => {
    expect(categoryOf(2, 29)).toBe('RAPID'); // 120 + 1160 s: un incremento alto sube de ritmo
    expect(categoryOf(0, 4)).toBe('BULLET'); // 160 s
    expect(categoryOf(0, 5)).toBe('BLITZ'); // 200 s
    expect(categoryOf(8, 0)).toBe('RAPID'); // 480 s
    expect(categoryOf(24, 59)).toBe('CLASSICAL'); // 1440 + 2360
  });

  it('nombra el ritmo con su categoría', () => {
    expect(timeControlLabel(3, 2)).toBe('Relámpago 3+2');
    expect(timeControlLabel(1, 0)).toBe('Bala 1+0');
    expect(PRESETS.map(([m, i]) => categoryOf(m, i))).toEqual(
      ['BULLET', 'BLITZ', 'BLITZ', 'RAPID', 'RAPID', 'RAPID', 'CLASSICAL']);
  });
});
