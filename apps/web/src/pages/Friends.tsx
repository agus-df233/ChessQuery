import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, EmptyState, Skeleton } from '@chessquery/ui-lib';
import { friendsApi } from '../api/users';
import { StatusMessage } from '../components/StatusMessage';

/** Mis amigos y las solicitudes pendientes (recibidas y enviadas). */
export const Friends = () => {
  const qc = useQueryClient();
  const friends = useQuery({ queryKey: ['friends'], queryFn: friendsApi.list });
  const incoming = useQuery({ queryKey: ['friends', 'incoming'], queryFn: () => friendsApi.requests('incoming') });
  const outgoing = useQuery({ queryKey: ['friends', 'outgoing'], queryFn: () => friendsApi.requests('outgoing') });
  const refresh = () => void qc.invalidateQueries({ queryKey: ['friends'] });
  const accept = useMutation({ mutationFn: friendsApi.accept, onSuccess: refresh });
  const decline = useMutation({ mutationFn: friendsApi.decline, onSuccess: refresh });
  const remove = useMutation({ mutationFn: friendsApi.remove, onSuccess: refresh });
  const error = accept.error ?? decline.error ?? remove.error;

  if (friends.isLoading) return <Skeleton height={200} />;
  return (
    <div className="cq-page">
      <h1>Amigos</h1>
      <StatusMessage error={error} />
      {(incoming.data?.length ?? 0) > 0 && (
        <Card header="Solicitudes recibidas">
          {incoming.data!.map((r) => (
            <div key={r.requestId} className="cq-row">
              <Link to={`/app/jugadores/${r.playerId}`}>{r.firstName} {r.lastName}</Link>
              <div className="cq-actions">
                <Button size="sm" onClick={() => accept.mutate(r.requestId)}>Aceptar</Button>
                <Button size="sm" variant="secondary" onClick={() => decline.mutate(r.requestId)}>Rechazar</Button>
              </div>
            </div>
          ))}
        </Card>
      )}
      <Card header={`Mis amigos (${friends.data?.length ?? 0})`}>
        {friends.data?.length ? friends.data.map((f) => (
          <div key={f.playerId} className="cq-row">
            <span><Link to={`/app/jugadores/${f.playerId}`}>{f.firstName} {f.lastName}</Link> <span className="cq-muted">{f.clubName ?? ''}</span></span>
            <div className="cq-actions">
              <span className="cq-muted">ELO {f.eloNational ?? '—'}</span>
              <Button size="sm" variant="secondary" onClick={() => remove.mutate(f.playerId)}>Quitar</Button>
            </div>
          </div>
        )) : <EmptyState title="Todavía no tienes amigos" description="Busca jugadores y envíales una solicitud." action={<Link to="/app/jugadores"><Button size="sm">Buscar jugadores</Button></Link>} />}
      </Card>
      {(outgoing.data?.length ?? 0) > 0 && (
        <Card header="Solicitudes enviadas">
          {outgoing.data!.map((r) => (
            <div key={r.requestId} className="cq-row">
              <Link to={`/app/jugadores/${r.playerId}`}>{r.firstName} {r.lastName}</Link>
              <Button size="sm" variant="secondary" onClick={() => remove.mutate(r.playerId)}>Cancelar</Button>
            </div>
          ))}
        </Card>
      )}
    </div>
  );
};
