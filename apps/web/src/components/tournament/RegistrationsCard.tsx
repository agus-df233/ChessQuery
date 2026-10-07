import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card } from '@chessquery/ui-lib';
import { tournamentsApi } from '../../api/tournaments';
import type { RegistrationStatus, RegistrationView, TournamentView } from '../../api/tournamentTypes';
import { StatusMessage } from '../StatusMessage';
import { tournamentKeys } from './TournamentDetailView';

export const registrationsKey = (id: number) => ['tournament-registrations', id] as const;

const SECTIONS: { status: RegistrationStatus; title: string }[] = [
  { status: 'PENDING', title: 'Esperan tu aprobación' },
  { status: 'WAITLIST', title: 'Lista de espera (entran solos si se libera un cupo)' },
  { status: 'CONFIRMED', title: 'Confirmados' },
  { status: 'WITHDRAWN', title: 'Retirados' },
];

const withdrawnLabel = (r: RegistrationView) =>
  (r.withdrawnFromRound === 1 ? 'no se presentó' : `retirado desde la ronda ${r.withdrawnFromRound}`);

/** Mutación del organizador que refresca inscripciones, el torneo y sus listas. */
export const useRegistrationAction = <T,>(id: number, fn: (arg: T) => Promise<unknown>) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      [...tournamentKeys(id), ['my-tournaments']].forEach((queryKey) => void qc.invalidateQueries({ queryKey }));
    },
  });
};

/** Acreditado o no (solo si el torneo exige acreditación) y el motivo de un retiro. */
const RowBadges = ({ t, r }: { t: TournamentView; r: RegistrationView }) => {
  if (r.status === 'WITHDRAWN') return <span className="cq-muted">· {withdrawnLabel(r)}</span>;
  if (r.status !== 'CONFIRMED' || !t.checkinRequired) return null;
  return r.checkedInAt ? <Badge variant="success">Acreditado</Badge> : <Badge>Sin acreditar</Badge>;
};

type RowProps = { t: TournamentView; r: RegistrationView };

const waiting = (r: RegistrationView) => r.status === 'PENDING' || r.status === 'WAITLIST';

/** Antes de empezar: aprobar a quien espera aprobación o cupo. */
const ApproveButton = ({ t, r }: RowProps) => {
  const approve = useRegistrationAction(t.id, () => tournamentsApi.approve(t.id, r.playerId));
  if (t.status !== 'OPEN' || !waiting(r)) return null;
  return (
    <>
      <Button size="sm" loading={approve.isPending} onClick={() => approve.mutate(undefined)}>Aprobar</Button>
      <StatusMessage error={approve.error} />
    </>
  );
};

/** Antes de empezar: rechazar (si espera) o quitar (si estaba confirmado); el cupo pasa a la lista de espera. */
const RemoveButton = ({ t, r }: RowProps) => {
  const remove = useRegistrationAction(t.id, () => tournamentsApi.unregister(t.id, r.playerId));
  if (t.status !== 'OPEN' || r.status === 'WITHDRAWN') return null;
  return (
    <>
      <Button size="sm" variant="secondary" loading={remove.isPending} onClick={() => remove.mutate(undefined)}>
        {waiting(r) ? 'Rechazar' : 'Quitar'}
      </Button>
      <StatusMessage error={remove.error} />
    </>
  );
};

/** Durante el torneo: retirar (deja de emparejarse desde la próxima ronda; sus resultados quedan). */
const WithdrawButton = ({ t, r }: RowProps) => {
  const withdraw = useRegistrationAction(t.id, () => tournamentsApi.withdraw(t.id, r.playerId));
  if (t.status !== 'IN_PROGRESS' || r.status !== 'CONFIRMED') return null;
  const confirm = () => {
    if (window.confirm(`¿Retirar a ${r.name}? Deja de emparejarse desde la próxima ronda.`)) withdraw.mutate(undefined);
  };
  return (
    <>
      <Button size="sm" variant="secondary" loading={withdraw.isPending} onClick={confirm}>Retirar</Button>
      <StatusMessage error={withdraw.error} />
    </>
  );
};

const RowActions = ({ t, r }: RowProps) => (
  <span className="cq-actions">
    <ApproveButton t={t} r={r} />
    <RemoveButton t={t} r={r} />
    <WithdrawButton t={t} r={r} />
  </span>
);

const Row = ({ t, r }: { t: TournamentView; r: RegistrationView }) => (
  <li className="cq-tournament-item">
    <span>
      {r.title ? `${r.title} ` : ''}{r.name} <span className="cq-muted">· {r.seedRating}</span> <RowBadges t={t} r={r} />
    </span>
    <RowActions t={t} r={r} />
  </li>
);

/** Inscripciones del torneo por estado, con aprobar, rechazar, retirar y el acceso a la acreditación. */
export const RegistrationsCard = ({ t }: { t: TournamentView }) => {
  const list = useQuery({ queryKey: registrationsKey(t.id), queryFn: () => tournamentsApi.registrations(t.id) });
  const all = list.data ?? [];
  const seats = t.maxPlayers ? `${t.playerCount + t.pendingCount}/${t.maxPlayers} cupos` : `${t.playerCount} confirmados`;
  return (
    <Card header={`Inscripciones · ${seats}`}>
      {t.status === 'OPEN' && (
        <div className="cq-actions" style={{ marginBottom: 8 }}>
          {t.checkinRequired && <Link to={`/club/torneos/${t.id}/acreditacion`}><Button size="sm">Acreditación con QR</Button></Link>}
          <Link to={`/club/torneos/${t.id}/credenciales`}><Button size="sm" variant="secondary">Credenciales para imprimir</Button></Link>
        </div>
      )}
      {all.length === 0 && <p className="cq-muted">Todavía no hay inscripciones.</p>}
      {SECTIONS.map(({ status, title }) => {
        const rows = all.filter((r) => r.status === status);
        if (rows.length === 0) return null;
        return (
          <section key={status} aria-label={title}>
            <h2 className="cq-muted" style={{ fontSize: 14, margin: '12px 0 4px' }}>{title} ({rows.length})</h2>
            <ul className="cq-list">{rows.map((r) => <Row key={r.playerId} t={t} r={r} />)}</ul>
          </section>
        );
      })}
    </Card>
  );
};
