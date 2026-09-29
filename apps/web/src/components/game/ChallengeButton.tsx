import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@chessquery/ui-lib';
import { gamesApi } from '../../api/games';
import type { ColorChoice } from '../../api/gameTypes';
import { StatusMessage } from '../StatusMessage';
import { TIME_CONTROLS } from './labels';

/** "Desafiar": elige ritmo y color y lleva a la partida (queda pendiente hasta que el rival acepte). */
export const ChallengeButton = ({ opponentId, opponentName }: { opponentId: number; opponentName: string }) => {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [tc, setTc] = useState(0);
  const [color, setColor] = useState<ColorChoice>('RANDOM');
  const challenge = useMutation({
    mutationFn: () => gamesApi.challenge({ opponentId, minutes: TIME_CONTROLS[tc][0], incrementSeconds: TIME_CONTROLS[tc][1], color, rated: true }),
    onSuccess: (g) => navigate(`/app/partidas/${g.id}`),
  });
  if (!open) return <Button size="sm" onClick={() => setOpen(true)}>Desafiar</Button>;
  return (
    <div className="cq-actions" role="group" aria-label={`Desafiar a ${opponentName}`}>
      <label>Ritmo
        <select value={tc} onChange={(e) => setTc(Number(e.target.value))}>
          {TIME_CONTROLS.map(([, , label], i) => <option key={label} value={i}>{label}</option>)}
        </select>
      </label>
      <label>Juego con
        <select value={color} onChange={(e) => setColor(e.target.value as ColorChoice)}>
          <option value="RANDOM">Al azar</option><option value="WHITE">Blancas</option><option value="BLACK">Negras</option>
        </select>
      </label>
      <Button size="sm" loading={challenge.isPending} onClick={() => challenge.mutate()}>Enviar desafío</Button>
      <Button size="sm" variant="secondary" onClick={() => setOpen(false)}>Cancelar</Button>
      <StatusMessage error={challenge.error} />
    </div>
  );
};
