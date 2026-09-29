import type { Profile, RosterCreateRequest } from '../api/types';

/**
 * Carga masiva del roster desde CSV (separador `,` o `;`, con o sin cabecera):
 *   nombre,apellido,email,rut,elo
 * La cabecera acepta alias (nombre/firstName, apellido/lastName, email/correo, rut,
 * elo/eloNacional/rating). Sin cabecera se asume ese orden.
 *
 * Devuelve un reporte por fila para la vista previa: `ok` se importa, `duplicate` se omite
 * (contra el roster existente y contra el propio archivo), `error` explica el motivo.
 */
export interface RosterCsvRow {
  line: number;
  raw: string;
  status: 'ok' | 'duplicate' | 'error';
  reason?: string;
  input?: RosterCreateRequest;
}

type Field = keyof RosterCreateRequest;

const HEADER_ALIASES: Record<string, Field> = {
  nombre: 'firstName', firstname: 'firstName',
  apellido: 'lastName', lastname: 'lastName',
  email: 'email', correo: 'email',
  rut: 'rut',
  elo: 'eloNational', elonacional: 'eloNational', rating: 'eloNational',
};
const DEFAULT_ORDER: Field[] = ['firstName', 'lastName', 'email', 'rut', 'eloNational'];

export const normalizeRut = (rut: string) => rut.replace(/\./g, '').replace(/\s/g, '').toUpperCase();

/** Clave de nombre sin tildes ni mayúsculas para detectar repetidos. */
const nameKey = (first: string, last: string) =>
  `${first.trim().toLowerCase()} ${last.trim().toLowerCase()}`.normalize('NFD').replace(/[̀-ͯ]/g, '');

const aliasOf = (cell: string) => HEADER_ALIASES[cell.toLowerCase().replace(/\s/g, '')];
const detectSeparator = (line: string) => (line.split(';').length > line.split(',').length ? ';' : ',');
const splitLine = (line: string, sep: string) => line.split(sep).map((c) => c.trim().replace(/^"(.*)"$/, '$1'));
const isHeader = (cells: string[]) => cells.some((c) => aliasOf(c) !== undefined);

/** Valores crudos de una fila, ya ubicados por columna según la cabecera (o el orden por defecto). */
type RawFields = Record<Field, string>;

const readFields = (cells: string[], order: Field[]): RawFields =>
  Object.fromEntries(DEFAULT_ORDER.map((f) => [f, cells[order.indexOf(f)] ?? ''])) as RawFields;

/**
 * Una regla por columna: devuelve el motivo del error o null. El orden importa (primero lo obligatorio).
 * Para agregar una validación nueva basta sumar una línea acá.
 */
const COLUMN_RULES: ((f: RawFields) => string | null)[] = [
  (f) => (!f.firstName || !f.lastName ? 'Faltan nombre o apellido' : null),
  (f) => (f.email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email) ? `Email inválido: ${f.email}` : null),
  (f) => (f.rut && !/^\d{7,8}-[\dK]$/.test(normalizeRut(f.rut)) ? `RUT inválido: ${normalizeRut(f.rut)}` : null),
  (f) => {
    const elo = Number(f.eloNational);
    return f.eloNational && (!Number.isInteger(elo) || elo <= 0) ? `ELO inválido: ${f.eloNational}` : null;
  },
];

const toInput = (f: RawFields): RosterCreateRequest => ({
  firstName: f.firstName,
  lastName: f.lastName,
  email: f.email || undefined,
  rut: f.rut ? normalizeRut(f.rut) : undefined,
  eloNational: f.eloNational ? Number(f.eloNational) : undefined,
});

/** Detecta repetidos contra el roster activo y contra las filas ya aceptadas del mismo archivo. */
class DuplicateGuard {
  private readonly ruts: Set<string>;
  private readonly names: Set<string>;

  constructor(existing: Profile[]) {
    const active = existing.filter((p) => p.active);
    this.ruts = new Set(active.filter((p) => p.rut).map((p) => normalizeRut(p.rut!)));
    this.names = new Set(active.map((p) => nameKey(p.firstName, p.lastName)));
  }

  isDuplicate(input: RosterCreateRequest): boolean {
    return (!!input.rut && this.ruts.has(input.rut)) || this.names.has(nameKey(input.firstName, input.lastName));
  }

  remember(input: RosterCreateRequest): void {
    if (input.rut) this.ruts.add(input.rut);
    this.names.add(nameKey(input.firstName, input.lastName));
  }
}

function classify(fields: RawFields, guard: DuplicateGuard): Omit<RosterCsvRow, 'line' | 'raw'> {
  const reason = COLUMN_RULES.map((rule) => rule(fields)).find((r) => r !== null);
  if (reason) return { status: 'error', reason };
  const input = toInput(fields);
  if (guard.isDuplicate(input)) return { status: 'duplicate', reason: 'Ya está en el roster o repetido en el archivo' };
  guard.remember(input);
  return { status: 'ok', input };
}

export function parseRosterCsv(text: string, existing: Profile[]): RosterCsvRow[] {
  const lines = text.split(/\r?\n/).map((raw, i) => ({ raw, line: i + 1 })).filter((l) => l.raw.trim());
  if (lines.length === 0) return [];
  const sep = detectSeparator(lines[0].raw);
  const firstCells = splitLine(lines[0].raw, sep);
  const hasHeader = isHeader(firstCells);
  const order = hasHeader ? firstCells.map((c) => aliasOf(c) ?? ('' as Field)) : DEFAULT_ORDER;
  const guard = new DuplicateGuard(existing);
  return lines.slice(hasHeader ? 1 : 0).map(({ raw, line }) => ({
    line, raw, ...classify(readFields(splitLine(raw, sep), order), guard),
  }));
}
