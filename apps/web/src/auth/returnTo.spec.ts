import { describe, expect, it } from 'vitest';
import { safeReturnPath } from './returnTo';

describe('volver después del login', () => {
  it('acepta rutas internas con su query', () => {
    expect(safeReturnPath('/app/salas?codigo=AB3K9Q')).toBe('/app/salas?codigo=AB3K9Q');
    expect(safeReturnPath('/app/desafio/abc')).toBe('/app/desafio/abc');
  });

  it('rechaza todo lo que pueda llevar a otro sitio', () => {
    for (const bad of ['//evil.com', '/\\evil.com', 'https://evil.com', 'evil.com', '', '/app\n', null, undefined, 42, {}]) {
      expect(safeReturnPath(bad), String(bad)).toBeNull();
    }
  });
});
