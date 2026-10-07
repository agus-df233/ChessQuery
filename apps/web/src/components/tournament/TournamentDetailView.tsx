import { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { publicTournamentsApi } from '../../api/tournaments';
import type { RoundView, TournamentDetail } from '../../api/tournamentTypes';
import { PlayersCard, RoundCard, StandingsCard, TournamentHeader } from './TournamentViews';
import { useLiveTournament } from './useLiveTournament';

/**
 * Claves de caché de un torneo; el organizador las invalida al cambiar algo. Incluye las inscripciones: generar la
 * ronda 1 deja como "no se presentó" a quien no se acreditó, y la lista tiene que mostrarlo.
 */
export const tournamentKeys = (id: number) => [['tournament', id], ['tournament-rounds', id], ['tournament-standings', id],
  ['tournament-registrations', id]];

/**
 * Datos de un torneo desde la API pública, siempre al día: {@link useLiveTournament} escucha los cambios (long polling
 * por versión) y actualiza estas mismas cachés, así que la sala, los apoderados y el organizador ven cada resultado sin
 * recargar.
 */
export const useTournament = (id: number) => {
  useLiveTournament(id);
  const detail = useQuery({ queryKey: ['tournament', id], queryFn: () => publicTournamentsApi.detail(id) });
  const inPlay = detail.data?.tournament.status !== 'OPEN';
  const rounds = useQuery({ queryKey: ['tournament-rounds', id], queryFn: () => publicTournamentsApi.rounds(id), enabled: inPlay });
  const standings = useQuery({ queryKey: ['tournament-standings', id], queryFn: () => publicTournamentsApi.standings(id), enabled: inPlay });
  return { detail, rounds, standings };
};

/**
 * Vista de un torneo (pública, del jugador y del organizador): cabecera, tabla, rondas (la última primero) e inscritos.
 * {@code actions} va bajo la cabecera; {@code renderResult} reemplaza el resultado solo en la ronda en curso
 * (las anteriores ya definieron pareos y no se corrigen).
 */
export const TournamentDetailView = ({ id, actions, renderResult }: {
  id: number; actions?: (d: TournamentDetail) => ReactNode;
  renderResult?: (round: RoundView) => ((b: RoundView['boards'][number]) => ReactNode) | undefined;
}) => {
  const { detail, rounds, standings } = useTournament(id);
  if (detail.isLoading) return <Skeleton height={240} />;
  if (detail.error || !detail.data) return <ErrorAlert message="No encontramos ese torneo" onRetry={() => void detail.refetch()} />;
  const d = detail.data;
  const ordered = [...(rounds.data ?? [])].reverse();
  return (
    <div className="cq-page">
      <TournamentHeader t={d.tournament} />
      {actions?.(d)}
      {d.tournament.status !== 'OPEN' && <StandingsCard rows={standings.data ?? []} />}
      {ordered.map((r, i) => (
        <RoundCard key={r.number} round={r}
                   renderResult={i === 0 && d.tournament.status === 'IN_PROGRESS' ? renderResult?.(r) : undefined} />
      ))}
      <PlayersCard players={d.players} />
    </div>
  );
};
