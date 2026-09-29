import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card } from '@chessquery/ui-lib';
import { isApiError } from '../api/client';
import { usersApi } from '../api/users';
import type { ClaimRequest, Profile } from '../api/types';
import { StatusMessage } from './StatusMessage';

/** Tras vincular o reclamar, el perfil, los ratings y el historial cambian. */
const useRefreshMe = () => {
  const qc = useQueryClient();
  return () => ['me', 'my-history', 'claim-suggestions'].forEach((k) => void qc.invalidateQueries({ queryKey: [k] }));
};

/**
 * Vincular mi ficha de la Federación. Si la ficha ya está en ChessQuery sin dueño (llegó por la ingesta),
 * la API responde CLAIM_REQUIRED y se pide el RUT para verificar que es mía antes de unirla a la cuenta.
 */
export const FederationCard = ({ profile }: { profile: Profile }) => {
  const refresh = useRefreshMe();
  const [federationId, setFederationId] = useState('');
  const [rut, setRut] = useState('');
  const link = useMutation({ mutationFn: usersApi.linkFederation, onSuccess: refresh });
  const claim = useMutation({ mutationFn: (body: ClaimRequest) => usersApi.claim(body), onSuccess: refresh });
  const needsClaim = isApiError(link.error) && link.error.error === 'CLAIM_REQUIRED';

  if (profile.federationId) {
    return (
      <Card header="Federación Chilena de Ajedrez">
        <p style={{ margin: 0 }}>Ficha federativa <strong>{profile.federationId}</strong></p>
        <p className="cq-muted">
          {profile.ratings.national ? `ELO nacional ${profile.ratings.national}` : 'Estamos trayendo tu ELO nacional desde la Federación.'}
        </p>
      </Card>
    );
  }
  const onLink = (e: FormEvent) => { e.preventDefault(); link.mutate(federationId.trim()); };
  const onClaim = (e: FormEvent) => { e.preventDefault(); claim.mutate({ federationId: federationId.trim(), rut }); };
  return (
    <Card header="Federación Chilena de Ajedrez">
      {needsClaim ? (
        <form className="cq-form" onSubmit={onClaim}>
          <p className="cq-muted" style={{ margin: 0 }}>
            Esa ficha ya está en ChessQuery. Para unirla a tu cuenta confirma tu RUT: lo comparamos con el de la ficha.
          </p>
          <label>RUT<input required value={rut} onChange={(e) => setRut(e.target.value)} placeholder="12.345.678-9" /></label>
          <div className="cq-actions">
            <Button type="submit" size="sm" loading={claim.isPending}>Reclamar mi ficha</Button>
            <StatusMessage error={claim.error} />
          </div>
        </form>
      ) : (
        <form className="cq-form" onSubmit={onLink}>
          <label>Mi id federativo
            <input required inputMode="numeric" pattern="\d{1,10}" value={federationId}
                   onChange={(e) => setFederationId(e.target.value)} placeholder="Ej: 738" />
          </label>
          <p className="cq-muted" style={{ margin: 0 }}>Con tu autorización consultamos solo tu ficha para traer tu ELO nacional y club.</p>
          <div className="cq-actions">
            <Button type="submit" size="sm" loading={link.isPending}>Vincular mi ficha</Button>
            <StatusMessage error={link.error} />
          </div>
        </form>
      )}
    </Card>
  );
};

/** "¿Eres tú?": fichas federadas sin dueño con mi nombre. Reclamar exige que coincidan año de nacimiento y nombre. */
export const ClaimSuggestions = () => {
  const refresh = useRefreshMe();
  const suggestions = useQuery({ queryKey: ['claim-suggestions'], queryFn: usersApi.claimSuggestions });
  const claim = useMutation({ mutationFn: (playerId: number) => usersApi.claim({ playerId }), onSuccess: refresh });
  if (!suggestions.data?.length) return null;
  return (
    <Card header="¿Eres tú?">
      <p className="cq-muted" style={{ marginTop: 0 }}>
        Encontramos fichas con tu nombre. Si una es tuya, únela a tu cuenta (necesitas tu fecha de nacimiento en el perfil).
      </p>
      <ul className="cq-list">
        {suggestions.data.map((s) => (
          <li key={s.id} className="cq-actions" style={{ justifyContent: 'space-between' }}>
            <span>
              {[s.currentTitle, s.firstName, s.lastName].filter(Boolean).join(' ')}
              <span className="cq-muted"> · {[s.club?.name, s.fideId && `FIDE ${s.fideId}`, s.ratings.fideStandard && `${s.ratings.fideStandard}`]
                .filter(Boolean).join(' · ')}</span>
            </span>
            <Button size="sm" variant="secondary" onClick={() => claim.mutate(s.id)}
                    loading={claim.isPending && claim.variables === s.id}>Soy yo</Button>
          </li>
        ))}
      </ul>
      <StatusMessage error={claim.error} success={claim.isSuccess ? 'Ficha unida a tu cuenta' : null} />
    </Card>
  );
};
