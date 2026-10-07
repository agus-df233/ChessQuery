import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card } from '@chessquery/ui-lib';
import { openChallengeUrl, openChallengesApi } from '../../api/games';
import type { ColorChoice, OpenChallengeView } from '../../api/gameTypes';
import { StatusMessage } from '../StatusMessage';
import { QrCode } from '../QrCode';
import { PRESETS, timeControlLabel } from '../../lib/timeControl';

const POLL_MS = 2_000;

/** Mientras el desafío está abierto, pregunta cada 2 s; cuando alguien lo acepta, lleva a la partida. */
export const useWaitForRival = (token: string | undefined) => {
  const navigate = useNavigate();
  const view = useQuery({
    queryKey: ['open-challenge', token],
    queryFn: () => openChallengesApi.get(token!),
    enabled: !!token,
    refetchInterval: (q) => (q.state.data?.status === 'OPEN' ? POLL_MS : false),
  });
  const gameId = view.data?.status === 'ACCEPTED' ? view.data.gameId : null;
  useEffect(() => { if (gameId) navigate(`/app/partidas/${gameId}`); }, [gameId, navigate]);
  return view;
};

/** Enlace + QR de un desafío publicado, esperando rival. */
export const WaitingForRival = ({ challenge, onCancel }: { challenge: OpenChallengeView; onCancel: () => void }) => {
  const url = openChallengeUrl(challenge.token);
  useWaitForRival(challenge.token);
  return (
    <div className="cq-room-code">
      <div>
        <p role="status" style={{ marginTop: 0 }}>Esperando rival: {timeControlLabel(challenge.minutes, challenge.incrementSeconds)}{challenge.rated ? ', por rating' : ', amistosa'}.</p>
        <p className="cq-muted">Comparte el enlace o muestra el QR. El primero que lo acepte juega contigo.</p>
        <input readOnly value={url} aria-label="Enlace del desafío" onFocus={(e) => e.target.select()} style={{ width: '100%' }} />
        <div className="cq-actions" style={{ marginTop: 8 }}>
          <Button size="sm" variant="secondary" onClick={() => void navigator.clipboard?.writeText(url)}>Copiar enlace</Button>
          <Button size="sm" variant="secondary" onClick={onCancel}>Cancelar desafío</Button>
        </div>
      </div>
      <QrCode value={url} label="QR del desafío abierto" />
    </div>
  );
};

/**
 * "Desafío abierto" en Mis partidas: para jugar con alguien que no es tu amigo (o que recién llega a ChessQuery).
 * Publicas un enlace con ritmo y color; quien lo abra con su cuenta acepta y empieza la partida.
 */
export const OpenChallengeCard = () => {
  const qc = useQueryClient();
  const [preset, setPreset] = useState(1); // 3+2
  const [color, setColor] = useState<ColorChoice>('RANDOM');
  const [rated, setRated] = useState(true);
  const mine = useQuery({ queryKey: ['open-challenges'], queryFn: openChallengesApi.mine });
  const refresh = () => void qc.invalidateQueries({ queryKey: ['open-challenges'] });
  const create = useMutation({
    mutationFn: () => openChallengesApi.create({ minutes: PRESETS[preset][0], incrementSeconds: PRESETS[preset][1], color, rated }),
    onSuccess: refresh,
  });
  const cancel = useMutation({ mutationFn: openChallengesApi.cancel, onSuccess: refresh });
  const current = mine.data?.[0];
  return (
    <Card header="Desafío abierto">
      {current ? <WaitingForRival challenge={current} onCancel={() => cancel.mutate(current.token)} /> : (
        <div className="cq-actions">
          <label>Ritmo del desafío abierto
            <select value={preset} onChange={(e) => setPreset(Number(e.target.value))}>
              {PRESETS.map(([m, i], idx) => <option key={`${m}+${i}`} value={idx}>{timeControlLabel(m, i)}</option>)}
            </select>
          </label>
          <label>Color
            <select value={color} onChange={(e) => setColor(e.target.value as ColorChoice)}>
              <option value="RANDOM">Al azar</option><option value="WHITE">Blancas</option><option value="BLACK">Negras</option>
            </select>
          </label>
          <label className="cq-check"><input type="checkbox" checked={rated} onChange={(e) => setRated(e.target.checked)} /> Por rating</label>
          <Button size="sm" loading={create.isPending} onClick={() => create.mutate()}>Crear enlace</Button>
        </div>
      )}
      <StatusMessage error={create.error ?? cancel.error} />
    </Card>
  );
};
