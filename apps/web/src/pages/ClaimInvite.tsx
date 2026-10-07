import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { claimApi } from '../api/users';
import { StatusMessage } from '../components/StatusMessage';

/**
 * /app/reclamar/:token: el jugador abre la invitación de su club (enlace o QR) con su cuenta, ve de qué perfil se
 * trata y lo une al suyo: sus torneos y ratings pasan a su cuenta.
 */
export const ClaimInvite = () => {
  const token = useParams().token ?? '';
  const qc = useQueryClient();
  const preview = useQuery({ queryKey: ['claim-invite', token], queryFn: () => claimApi.preview(token), retry: false });
  const claim = useMutation({ mutationFn: () => claimApi.claim(token), onSuccess: () => void qc.invalidateQueries({ queryKey: ['me'] }) });

  if (preview.isLoading) return <Skeleton height={160} />;
  if (!preview.data) return <ErrorAlert message="Esta invitación no existe, ya se usó o venció: pídele una nueva a tu club" />;
  const p = preview.data;
  return (
    <div className="cq-page">
      <h1>¿Eres {p.firstName} {p.lastName}?</h1>
      <Card>
        {claim.isSuccess ? (
          <p role="status" className="cq-ok">Listo: el perfil quedó unido a tu cuenta. <Link to="/app">Ir a mi inicio</Link></p>
        ) : (
          <>
            <p>{p.organizationName ?? 'Tu club'} te cargó en su roster. Si eres tú, une ese perfil a tu cuenta: tus torneos
              y ratings pasan a tu perfil de ChessQuery.</p>
            <Button loading={claim.isPending} onClick={() => claim.mutate()}>Sí, soy yo: unirlo a mi cuenta</Button>
          </>
        )}
        <StatusMessage error={claim.error} />
      </Card>
    </div>
  );
};
