import { useCallback, useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { roomsApi } from '../../api/rooms';
import type { RoomView } from '../../api/roomTypes';
import { openLiveChannel } from '../game/useLiveGame';

const RETRY_AFTER_ERROR_MS = 3_000;

export const roomKey = (id: number) => ['room', id] as const;

/**
 * Sala en vivo, igual que una partida: con `VITE_WS_URL` llega cada cambio por **WebSocket** (suscripción a la sala:
 * miembros, tableros y cada jugada); mientras el socket no esté arriba, **long polling** por versión. Ambas vías
 * actualizan la misma caché y solo aceptan versiones más nuevas. Una sala cerrada deja de escuchar.
 */
export const useLiveRoom = (id: number) => {
  const qc = useQueryClient();
  const [round, setRound] = useState(0);
  const [socketUp, setSocketUp] = useState(false);
  const room = useQuery({ queryKey: roomKey(id), queryFn: () => roomsApi.get(id) });
  const version = room.data?.version;
  const live = room.data?.status === 'OPEN';

  const apply = useCallback((latest: RoomView) => {
    qc.setQueryData<RoomView>(roomKey(id), (prev) => (prev && prev.version > latest.version ? prev : latest));
  }, [qc, id]);

  const wsUrl = import.meta.env.VITE_WS_URL as string | undefined;
  useEffect(() => {
    if (!wsUrl || !live) return undefined;
    const close = openLiveChannel(wsUrl, { roomId: id }, (msg) => {
      const latest = msg.room as RoomView | undefined;
      if (msg.type === 'room' && latest?.id === id) apply(latest);
    }, setSocketUp);
    return () => { close(); setSocketUp(false); };
  }, [wsUrl, id, live, apply]);

  useEffect(() => {
    if (!live || version === undefined || socketUp) return undefined;
    const controller = new AbortController();
    let retry: ReturnType<typeof setTimeout> | undefined;
    const next = () => setRound((r) => r + 1);
    roomsApi.waitChange(id, version, controller.signal)
      .then((latest) => { apply(latest); next(); })
      .catch(() => {
        if (!controller.signal.aborted) retry = setTimeout(next, RETRY_AFTER_ERROR_MS);
      });
    return () => { controller.abort(); clearTimeout(retry); };
  }, [id, version, live, socketUp, apply, round]);

  return room;
};
