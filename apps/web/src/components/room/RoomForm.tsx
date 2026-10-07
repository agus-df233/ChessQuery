import { FormEvent, useState } from 'react';
import { Button } from '@chessquery/ui-lib';
import type { RoomRequest } from '../../api/roomTypes';
import { StatusMessage } from '../StatusMessage';
import { PRESETS, timeControlLabel } from '../../lib/timeControl';

export const EMPTY_ROOM: RoomRequest = { name: '', boards: 4, maxPlayers: 8, minutes: 10, incrementSeconds: 5 };

/**
 * Datos de una sala: nombre, tableros (1–16), cupo de jugadores (2–64; puede superar 2 por tablero: el resto mira) y
 * ritmo (las partidas de sala nunca cuentan para el ELO). Sirve para crear y para reconfigurar.
 */
export const RoomForm = ({ initial, submitLabel, pending, error, onSubmit }: {
  initial: RoomRequest; submitLabel: string; pending: boolean; error: unknown; onSubmit: (req: RoomRequest) => void;
}) => {
  const [form, setForm] = useState<RoomRequest>(initial);
  const set = (patch: Partial<RoomRequest>) => setForm({ ...form, ...patch });
  const preset = PRESETS.findIndex(([m, i]) => m === form.minutes && i === form.incrementSeconds);
  const submit = (e: FormEvent) => { e.preventDefault(); onSubmit(form); };
  return (
    <form onSubmit={submit}>
      <div className="cq-form">
        <label>Nombre de la sala
          <input required maxLength={120} value={form.name} onChange={(e) => set({ name: e.target.value })} placeholder="Clase 4°B" />
        </label>
        <label>Tableros
          <input type="number" min={1} max={16} required value={form.boards} onChange={(e) => set({ boards: Number(e.target.value) })} />
        </label>
        <label>Cupo de jugadores
          <input type="number" min={2} max={64} required value={form.maxPlayers ?? ''} onChange={(e) => set({ maxPlayers: Number(e.target.value) })} />
        </label>
        <label>Ritmo
          <select value={preset} onChange={(e) => {
            const [minutes, incrementSeconds] = PRESETS[Number(e.target.value)] ?? [form.minutes, form.incrementSeconds];
            set({ minutes, incrementSeconds });
          }}>
            {preset < 0 && <option value={-1}>{timeControlLabel(form.minutes, form.incrementSeconds)}</option>}
            {PRESETS.map(([m, i], idx) => <option key={`${m}+${i}`} value={idx}>{timeControlLabel(m, i)}</option>)}
          </select>
        </label>
      </div>
      <p className="cq-muted" aria-live="polite">
        {form.boards * 2} jugadores juegan a la vez; {Math.max(0, (form.maxPlayers ?? 0) - form.boards * 2)} más pueden mirar.
        Las partidas de la sala no cuentan para el ELO.
      </p>
      <div className="cq-actions">
        <Button type="submit" loading={pending}>{submitLabel}</Button>
        <StatusMessage error={error} />
      </div>
    </form>
  );
};
