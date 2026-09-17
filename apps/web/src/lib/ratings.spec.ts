import { describe, expect, it } from 'vitest';
import { categoryLabel, displayName, RATING_GROUPS } from './ratings';

describe('helpers de ratings', () => {
  it('antepone el título al nombre', () => {
    expect(displayName({ firstName: 'Ana', lastName: 'Soto', currentTitle: 'FM' })).toBe('FM Ana Soto');
    expect(displayName({ firstName: 'Ana', lastName: 'Soto' })).toBe('Ana Soto');
  });
  it('etiqueta categorías y cubre las 13 modalidades', () => {
    expect(categoryLabel('SUB_12')).toBe('Sub 12');
    expect(categoryLabel('ADULTO')).toBe('Adulto');
    expect(RATING_GROUPS.flatMap((g) => g.items)).toHaveLength(13);
  });
});
