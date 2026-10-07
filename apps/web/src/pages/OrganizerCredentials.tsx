import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { publicTournamentsApi, tournamentsApi } from '../api/tournaments';
import { QrCode } from '../components/QrCode';
import { registrationsKey } from '../components/tournament/RegistrationsCard';

/**
 * /club/torneos/:id/credenciales: una credencial por jugador confirmado con su QR de acreditación, para imprimir y
 * entregar a quien no tiene cuenta o celular (p. ej. alumnos menores cargados desde el roster). El QR solo lleva el
 * código de la inscripción: ningún dato personal.
 */
export const OrganizerCredentials = () => {
  const id = Number(useParams().id);
  const tournament = useQuery({ queryKey: ['tournament', id], queryFn: () => publicTournamentsApi.detail(id) });
  const list = useQuery({ queryKey: registrationsKey(id), queryFn: () => tournamentsApi.registrations(id) });
  if (tournament.isLoading || list.isLoading) return <Skeleton height={320} />;
  if (!tournament.data || !list.data) return <ErrorAlert message="No encontramos ese torneo" onRetry={() => void list.refetch()} />;
  const confirmed = list.data.filter((r) => r.status === 'CONFIRMED');
  return (
    <div className="cq-page">
      <div className="cq-actions cq-no-print">
        <Link to={`/club/torneos/${id}`}>← {tournament.data.tournament.name}</Link>
        <Button size="sm" onClick={() => window.print()}>Imprimir</Button>
      </div>
      <h1>Credenciales · {tournament.data.tournament.name}</h1>
      <div className="cq-credentials">
        {confirmed.map((r) => (
          <article key={r.playerId} className="cq-credential" aria-label={`Credencial de ${r.name}`}>
            <strong>{r.name}</strong>
            <span className="cq-muted">{r.clubName ?? 'Sin club'}</span>
            <QrCode value={r.checkinCode} label={`QR de acreditación de ${r.name}`} size={140} />
            <code style={{ fontSize: 11 }}>{r.checkinCode}</code>
          </article>
        ))}
      </div>
    </div>
  );
};
