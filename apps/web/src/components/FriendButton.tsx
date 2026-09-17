import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@chessquery/ui-lib';
import { friendsApi } from '../api/users';
import { StatusMessage } from './StatusMessage';

/** Botón de amistad en el perfil de otro jugador: Agregar / Pendiente / Aceptar / Quitar. */
export const FriendButton = ({ otherId }: { otherId: number }) => {
  const qc = useQueryClient();
  const status = useQuery({ queryKey: ['friend-status', otherId], queryFn: () => friendsApi.status(otherId) });
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ['friend-status', otherId] });
    void qc.invalidateQueries({ queryKey: ['friends'] });
  };
  const request = useMutation({ mutationFn: () => friendsApi.request(otherId), onSuccess: invalidate });
  const accept = useMutation({ mutationFn: (id: number) => friendsApi.accept(id), onSuccess: invalidate });
  const remove = useMutation({ mutationFn: () => friendsApi.remove(otherId), onSuccess: invalidate });
  const busy = request.isPending || accept.isPending || remove.isPending;
  const error = request.error ?? accept.error ?? remove.error;

  if (!status.data) return null;
  const { status: s, requestId } = status.data;
  return (
    <div className="cq-actions">
      {s === 'NONE' && <Button onClick={() => request.mutate()} loading={busy}>Agregar amigo</Button>}
      {s === 'PENDING_OUT' && <Button variant="secondary" onClick={() => remove.mutate()} loading={busy}>Cancelar solicitud</Button>}
      {s === 'PENDING_IN' && requestId != null && (
        <Button onClick={() => accept.mutate(requestId)} loading={busy}>Aceptar solicitud</Button>
      )}
      {s === 'FRIENDS' && <Button variant="secondary" onClick={() => remove.mutate()} loading={busy}>Dejar de ser amigos</Button>}
      <StatusMessage error={error} />
    </div>
  );
};
