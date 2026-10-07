import type { TournamentRequest } from '../../api/tournamentTypes';

/** "2026-11-01T18:00" (input datetime-local, hora local) ↔ ISO en UTC (lo que guarda la API). */
export const toLocalInput = (iso?: string | null) => {
  if (!iso) return '';
  const d = new Date(iso);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
};
export const fromLocalInput = (value: string) => (value ? new Date(value).toISOString() : null);

const optionalNumber = (value: string) => (value === '' ? null : Number(value));

/**
 * Reglas de inscripción de un torneo: cupo (con lista de espera), cierre, aprobación del organizador, rango de rating
 * y acreditación el día del torneo (quien no se acredita no juega la ronda 1). Vacío = sin límite.
 */
export const RegistrationRules = ({ form, set }: { form: TournamentRequest; set: (patch: Partial<TournamentRequest>) => void }) => (
  <fieldset className="cq-form" style={{ border: 0, padding: 0, margin: '12px 0 0' }}>
    <legend className="cq-muted">Inscripción (vacío = sin límite)</legend>
    <label>Cupo de jugadores
      <input type="number" min={2} max={500} value={form.maxPlayers ?? ''} onChange={(e) => set({ maxPlayers: optionalNumber(e.target.value) })} />
    </label>
    <label>Cierre de inscripción
      <input type="datetime-local" value={toLocalInput(form.registrationClosesAt)} onChange={(e) => set({ registrationClosesAt: fromLocalInput(e.target.value) })} />
    </label>
    <label>Rating mínimo
      <input type="number" min={0} max={3500} value={form.minRating ?? ''} onChange={(e) => set({ minRating: optionalNumber(e.target.value) })} />
    </label>
    <label>Rating máximo
      <input type="number" min={0} max={3500} value={form.maxRating ?? ''} onChange={(e) => set({ maxRating: optionalNumber(e.target.value) })} />
    </label>
    <label className="cq-check">
      <input type="checkbox" checked={!!form.requiresApproval} onChange={(e) => set({ requiresApproval: e.target.checked })} /> Apruebo cada inscripción
    </label>
    <label className="cq-check">
      <input type="checkbox" checked={!!form.checkinRequired} onChange={(e) => set({ checkinRequired: e.target.checked })} /> Acreditación con QR el día del torneo
    </label>
  </fieldset>
);
