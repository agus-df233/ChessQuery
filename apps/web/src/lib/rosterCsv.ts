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

const HEADER_ALIASES: Record<string, keyof RosterCreateRequest> = {
  nombre: 'firstName', firstname: 'firstName',
  apellido: 'lastName', lastname: 'lastName',
  email: 'email', correo: 'email',
  rut: 'rut',
  elo: 'eloNational', elonacional: 'eloNational', rating: 'eloNational',
};
const DEFAULT_ORDER: (keyof RosterCreateRequest)[] = ['firstName', 'lastName', 'email', 'rut', 'eloNational'];

export const normalizeRut = (rut: string) => rut.replace(/\./g, '').replace(/\s/g, '').toUpperCase();

/** Clave de nombre sin tildes ni mayúsculas para detectar repetidos. */
const nameKey = (first: string, last: string) =>
  `${first.trim().toLowerCase()} ${last.trim().toLowerCase()}`.normalize('NFD').replace(/[̀-ͯ]/g, '');

const detectSeparator = (line: string) => (line.split(';').length > line.split(',').length ? ';' : ',');
const splitLine = (line: string, sep: string) => line.split(sep).map((c) => c.trim().replace(/^"(.*)"$/, '$1'));
const isHeader = (cells: string[]) => cells.some((c) => HEADER_ALIASES[c.toLowerCase().replace(/\s/g, '')] !== undefined);

export function parseRosterCsv(text: string, existing: Profile[]): RosterCsvRow[] {
  const lines = text.split(/\r?\n/);
  const rows: RosterCsvRow[] = [];
  const seenRuts = new Set(existing.filter((p) => p.active && p.rut).map((p) => normalizeRut(p.rut!)));
  const seenNames = new Set(existing.filter((p) => p.active).map((p) => nameKey(p.firstName, p.lastName)));

  let order = DEFAULT_ORDER;
  let started = false;
  const sep = detectSeparator(lines.find((l) => l.trim()) ?? '');

  lines.forEach((raw, i) => {
    const line = i + 1;
    if (!raw.trim()) return;
    const cells = splitLine(raw, sep);
    if (!started) {
      started = true;
      if (isHeader(cells)) {
        order = cells.map((c) => HEADER_ALIASES[c.toLowerCase().replace(/\s/g, '')] ?? ('' as keyof RosterCreateRequest));
        return;
      }
    }
    const get = (field: keyof RosterCreateRequest) => cells[order.indexOf(field)] ?? '';
    const firstName = get('firstName');
    const lastName = get('lastName');
    if (!firstName || !lastName) {
      rows.push({ line, raw, status: 'error', reason: 'Faltan nombre o apellido' });
      return;
    }
    const email = get('email') || undefined;
    if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      rows.push({ line, raw, status: 'error', reason: `Email inválido: ${email}` });
      return;
    }
    const rut = get('rut') ? normalizeRut(get('rut')) : undefined;
    if (rut && !/^\d{7,8}-[\dK]$/.test(rut)) {
      rows.push({ line, raw, status: 'error', reason: `RUT inválido: ${rut}` });
      return;
    }
    const eloRaw = get('eloNational');
    const eloNational = eloRaw ? Number(eloRaw) : undefined;
    if (eloRaw && (!Number.isInteger(eloNational) || eloNational! <= 0)) {
      rows.push({ line, raw, status: 'error', reason: `ELO inválido: ${eloRaw}` });
      return;
    }
    if ((rut && seenRuts.has(rut)) || seenNames.has(nameKey(firstName, lastName))) {
      rows.push({ line, raw, status: 'duplicate', reason: 'Ya está en el roster o repetido en el archivo' });
      return;
    }
    if (rut) seenRuts.add(rut);
    seenNames.add(nameKey(firstName, lastName));
    rows.push({ line, raw, status: 'ok', input: { firstName, lastName, email, rut, eloNational } });
  });
  return rows;
}
