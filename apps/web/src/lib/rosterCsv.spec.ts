import { describe, expect, it } from 'vitest';
import { parseRosterCsv, normalizeRut } from './rosterCsv';
import type { Profile } from '../api/types';

const existing = (over: Partial<Profile>): Profile => ({
  id: 1, firstName: 'Ana', lastName: 'Soto', displayName: null, email: null, rut: null, birthDate: null, gender: null,
  region: null, country: null, club: null, fideId: null, federationId: null, lichessUsername: null, chesscomUsername: null,
  ratings: {} as Profile['ratings'], currentTitle: null, ageCategory: 'ADULTO', enrichmentSource: null, enrichedAt: null,
  provisional: true, createdByOrganizerId: 1, active: true, tags: [], createdAt: '', updatedAt: '', ...over,
});

describe('parseRosterCsv', () => {
  it('acepta cabecera con alias y separador ;', () => {
    const rows = parseRosterCsv('Nombre;Apellido;Correo;RUT;Rating\nPedro;Rojas;p@x.cl;11.111.111-1;1500', []);
    expect(rows).toHaveLength(1);
    expect(rows[0].status).toBe('ok');
    expect(rows[0].input).toEqual({ firstName: 'Pedro', lastName: 'Rojas', email: 'p@x.cl', rut: '11111111-1', eloNational: 1500 });
  });

  it('sin cabecera usa el orden posicional y reporta errores por fila', () => {
    const rows = parseRosterCsv('Pedro,Rojas,,\n,Solo,\nAna,Soto,mal-email,,\nLuis,Paz,,123,\nEva,Mora,,,abc', []);
    expect(rows.map((r) => r.status)).toEqual(['ok', 'error', 'error', 'error', 'error']);
    expect(rows[1].reason).toMatch(/nombre/i);
    expect(rows[2].reason).toMatch(/Email/);
    expect(rows[3].reason).toMatch(/RUT/);
    expect(rows[4].reason).toMatch(/ELO/);
  });

  it('marca duplicados contra el roster existente y dentro del archivo', () => {
    const rows = parseRosterCsv('Ana,Soto\nPedro,Rojas,,11111111-1\npedro,rojas\nLuis,Paz,,11111111-1',
        [existing({ rut: null }), existing({ id: 2, firstName: 'X', lastName: 'Y', active: false })]);
    expect(rows.map((r) => r.status)).toEqual(['duplicate', 'ok', 'duplicate', 'duplicate']);
  });

  it('normaliza el RUT e ignora líneas vacías', () => {
    expect(normalizeRut(' 12.345.678-k ')).toBe('12345678-K');
    expect(parseRosterCsv('\n\nA,B\n\n', [])).toHaveLength(1);
  });
});
