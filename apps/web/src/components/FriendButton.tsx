import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, useToast } from '@chessquery/ui-lib';
import { friendsApi } from '../api/users';
import { StatusMessage } from './StatusMessage';

/** Las tres acciones posibles sobre la amistad con `otherId`, con estado combinado de carga y error. */
const useFriendActions = (otherId: number) => {
  const qc = useQueryClient();
  const toast = useToast();
  const done = (message: string) => () => {
    void qc.invalidateQueries({ queryKey: ['friend-status', otherId] });
    void qc.invalidateQueries({ queryKey: ['friends'] });
    toast.success(message);
  };
  const request = useMutation({ mutationFn: () => friendsApi.request(otherId), onSuccess: done('Solicitud de amistad enviada') });
  const accept = useMutation({ mutationFn: (id: number) => friendsApi.accept(id), onSuccess: done('Ahora son amigos') });
  const remove = useMutation({ mutationFn: () => friendsApi.remove(otherId), onSuccess: done('Listo: ya no aparecen como amigos') });
  const all = [request, accept, remove];
  return { request, accept, remove, busy: all.some((m) => m.isPending), error: all.find((m) => m.error)?.error };
};

/** Botón de amistad en el perfil de otro jugador: Agregar / Pendiente / Aceptar / Quitar. */
export const FriendButton = ({ otherId }: { otherId: number }) => {
  const status = useQuery({ queryKey: ['friend-status', otherId], queryFn: () => friendsApi.status(otherId) });
  const { request, accept, remove, busy, error } = useFriendActions(otherId);
  if (!status.data) return null;
  const { status: s, requestId } = status.data;
  // Qué botón corresponde a cada estado de la relación (PENDING_IN sin id no tiene acción posible).
  const actions: Record<string, { label: string; run: () => void; secondary?: boolean } | null> = {
    NONE: { label: 'Agregar amigo', run: () => request.mutate() },
    PENDING_OUT: { label: 'Cancelar solicitud', run: () => remove.mutate(), secondary: true },
    PENDING_IN: requestId != null ? { label: 'Aceptar solicitud', run: () => accept.mutate(requestId) } : null,
    FRIENDS: { label: 'Dejar de ser amigos', run: () => remove.mutate(), secondary: true },
  };
  const action = actions[s];
  return (
    <div className="cq-actions">
      {action && <Button variant={action.secondary ? 'secondary' : undefined} onClick={action.run} loading={busy}>{action.label}</Button>}
      <StatusMessage error={error} />
    </div>
  );
};
