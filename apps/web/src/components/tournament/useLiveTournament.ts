import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { publicTournamentsApi } from '../../api/tournaments';
import type { LiveView } from '../../api/tournamentTypes';

const RETRY_AFTER_ERROR_MS = 3_000;

export const liveKey = (id: number) => ['tournament-live', id] as const;

/**
 * Torneo en vivo para la pantalla de la sala, los apoderados y el organizador: encadena long polls por versión (el
 * servidor responde apenas cambia algo o a los 25 s) y reparte cada estado nuevo en las mismas cachés que usan las
 * vistas (detalle, rondas, tabla). Un torneo terminado deja de escuchar.
 */
export const useLiveTournament = (id: number) => {
  const qc = useQueryClient();
  const [round, setRound] = useState(0);
  const live = useQuery({ queryKey: liveKey(id), queryFn: () => publicTournamentsApi.live(id) });
  const version = live.data?.version;
  const listening = !!live.data && live.data.detail.tournament.status !== 'FINISHED';

  useEffect(() => {
    if (!live.data) return;
    qc.setQueryData(['tournament', id], live.data.detail);
    qc.setQueryData(['tournament-rounds', id], live.data.rounds);
    qc.setQueryData(['tournament-standings', id], live.data.standings);
  }, [qc, id, live.data]);

  useEffect(() => {
    if (!listening || version === undefined) return undefined;
    const controller = new AbortController();
    let retry: ReturnType<typeof setTimeout> | undefined;
    const next = () => setRound((r) => r + 1);
    publicTournamentsApi.waitLive(id, version, controller.signal)
      .then((latest: LiveView) => {
        qc.setQueryData<LiveView>(liveKey(id), (prev) => (prev && prev.version > latest.version ? prev : latest));
        next();
      })
      .catch(() => { if (!controller.signal.aborted) retry = setTimeout(next, RETRY_AFTER_ERROR_MS); });
    return () => { controller.abort(); clearTimeout(retry); };
  }, [qc, id, version, listening, round]);

  return live;
};
