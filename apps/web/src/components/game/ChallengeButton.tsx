import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@chessquery/ui-lib';
import { gamesApi } from '../../api/games';
import type { ColorChoice } from '../../api/gameTypes';
import { StatusMessage } from '../StatusMessage';
import { CATEGORY_LABEL, LIMITS, PRESETS, categoryOf, timeControlLabel } from '../../lib/timeControl';

const CUSTOM = -1;

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, Math.round(value) || min));

/**
 * "Desafiar": elige ritmo (un preset o uno personalizado) y color, y lleva a la partida (queda pendiente hasta que
 * el rival acepte). El ritmo define qué ELO ChessQuery se juega, y se muestra antes de enviar.
 */
export const ChallengeButton = ({ opponentId, opponentName }: { opponentId: number; opponentName: string }) => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [preset, setPreset] = useState(1); // 3+2
  const [custom, setCustom] = useState({ minutes: 10, increment: 5 });
  const [color, setColor] = useState<ColorChoice>('RANDOM');
  const [minutes, increment] = preset === CUSTOM ? [custom.minutes, custom.increment] : PRESETS[preset];
  const challenge = useMutation({
    mutationFn: () => gamesApi.challenge({ opponentId, minutes, incrementSeconds: increment, color, rated: true }),
    onSuccess: (g) => navigate(`/app/partidas/${g.id}`),
  });
  if (!open) return <Button size="sm" onClick={() => setOpen(true)}>Desafiar</Button>;
  return (
    <div className="cq-actions" role="group" aria-label={`Desafiar a ${opponentName}`}>
      <label>Ritmo
        <select value={preset} onChange={(e) => setPreset(Number(e.target.value))}>
          {PRESETS.map(([m, i], idx) => <option key={`${m}+${i}`} value={idx}>{timeControlLabel(m, i)}</option>)}
          <option value={CUSTOM}>Personalizado</option>
        </select>
      </label>
      {preset === CUSTOM && (
        <>
          <label>Minutos
            <input type="number" min={LIMITS.minMinutes} max={LIMITS.maxMinutes} value={custom.minutes}
              onChange={(e) => setCustom({ ...custom, minutes: clamp(Number(e.target.value), LIMITS.minMinutes, LIMITS.maxMinutes) })} />
          </label>
          <label>Incremento (s)
            <input type="number" min={0} max={LIMITS.maxIncrement} value={custom.increment}
              onChange={(e) => setCustom({ ...custom, increment: clamp(Number(e.target.value), 0, LIMITS.maxIncrement) })} />
          </label>
        </>
      )}
      <label>Juego con
        <select value={color} onChange={(e) => setColor(e.target.value as ColorChoice)}>
          <option value="RANDOM">Al azar</option><option value="WHITE">Blancas</option><option value="BLACK">Negras</option>
        </select>
      </label>
      <span className="cq-muted" aria-live="polite">Partida {CATEGORY_LABEL[categoryOf(minutes, increment)].toLowerCase()}: cuenta para tu ELO de ese ritmo</span>
      <Button size="sm" loading={challenge.isPending} onClick={() => challenge.mutate()}>Enviar desafío</Button>
      <Button size="sm" variant="secondary" onClick={() => setOpen(false)}>Cancelar</Button>
      <StatusMessage error={challenge.error} />
    </div>
  );
};
