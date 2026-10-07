import { FormEvent, useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { publicTournamentsApi, tournamentsApi } from '../api/tournaments';
import type { CheckinResult, RegistrationView } from '../api/tournamentTypes';
import { StatusMessage } from '../components/StatusMessage';
import { QrScanner } from '../components/tournament/QrScanner';
import { registrationsKey, useRegistrationAction } from '../components/tournament/RegistrationsCard';

/** Lo que dice la pantalla tras leer un QR (también para lectores de pantalla). */
const resultMessage = (r: CheckinResult) =>
  (r.alreadyCheckedIn ? `${r.registration.name} ya estaba acreditado` : `${r.registration.name} acreditado`);

/**
 * /club/torneos/:id/acreditacion: el día del torneo, el organizador lee con la cámara el QR de cada jugador (o
 * ingresa el código, o lo marca en la lista si no trae celular). Quien no se acredite no juega la ronda 1.
 */
export const OrganizerCheckin = () => {
  const id = Number(useParams().id);
  const tournament = useQuery({ queryKey: ['tournament', id], queryFn: () => publicTournamentsApi.detail(id) });
  const list = useQuery({ queryKey: registrationsKey(id), queryFn: () => tournamentsApi.registrations(id) });
  const [last, setLast] = useState<CheckinResult | null>(null);
  const [code, setCode] = useState('');
  const [filter, setFilter] = useState('');
  const scan = useRegistrationAction(id, (value: string) => tournamentsApi.checkin(id, value));
  const toggle = useRegistrationAction(id, (r: RegistrationView) =>
    (r.checkedInAt ? tournamentsApi.undoCheckin(id, r.playerId) : tournamentsApi.checkinManually(id, r.playerId)));
  const onCode = useCallback((value: string) => scan.mutate(value, { onSuccess: (r) => setLast(r as CheckinResult) }), [scan.mutate]); // mutate es estable entre renders

  if (tournament.isLoading || list.isLoading) return <Skeleton height={320} />;
  if (!tournament.data || !list.data) return <ErrorAlert message="No encontramos ese torneo" onRetry={() => void list.refetch()} />;
  const confirmed = list.data.filter((r) => r.status === 'CONFIRMED');
  const done = confirmed.filter((r) => r.checkedInAt).length;
  const shown = confirmed.filter((r) => r.name.toLowerCase().includes(filter.trim().toLowerCase()));
  const submit = (e: FormEvent) => { e.preventDefault(); onCode(code); setCode(''); };
  return (
    <div className="cq-page">
      <p><Link to={`/club/torneos/${id}`}>← {tournament.data.tournament.name}</Link></p>
      <h1>Acreditación</h1>
      <p role="status" aria-live="polite"><strong>{done} de {confirmed.length}</strong> jugadores acreditados.
        {last && <> Último: <strong>{resultMessage(last)}</strong>.</>}</p>
      <Card header="Leer el QR del jugador">
        <QrScanner onCode={onCode} />
        <form className="cq-actions" onSubmit={submit} style={{ marginTop: 8 }}>
          <label>Código de acreditación
            <input value={code} onChange={(e) => setCode(e.target.value)} autoComplete="off" />
          </label>
          <Button type="submit" size="sm" disabled={!code.trim()} loading={scan.isPending}>Acreditar</Button>
        </form>
        <StatusMessage error={scan.error} />
      </Card>
      <Card header="Lista de confirmados">
        <label>Buscar jugador<input value={filter} onChange={(e) => setFilter(e.target.value)} /></label>
        <ul className="cq-list">
          {shown.map((r) => (
            <li key={r.playerId} className="cq-tournament-item">
              <span>{r.name} {r.checkedInAt ? <Badge variant="success">Acreditado</Badge> : <Badge>Sin acreditar</Badge>}</span>
              <Button size="sm" variant={r.checkedInAt ? 'secondary' : 'primary'} onClick={() => toggle.mutate(r)}>
                {r.checkedInAt ? `Deshacer acreditación de ${r.name}` : `Acreditar a ${r.name}`}
              </Button>
            </li>
          ))}
        </ul>
        <StatusMessage error={toggle.error} />
      </Card>
    </div>
  );
};
