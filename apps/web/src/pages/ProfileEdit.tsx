import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, Skeleton } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { usersApi } from '../api/users';
import type { UpdateProfileRequest } from '../api/types';
import { StatusMessage } from '../components/StatusMessage';

/** Edición del propio perfil. Solo se envían los campos que el usuario tocó. */
export const ProfileEdit = () => {
  const qc = useQueryClient();
  const me = useMe();
  const countries = useQuery({ queryKey: ['countries'], queryFn: usersApi.countries, staleTime: Infinity });
  const clubs = useQuery({ queryKey: ['clubs'], queryFn: usersApi.clubs, staleTime: Infinity });
  const [changes, setChanges] = useState<UpdateProfileRequest>({});
  const save = useMutation({
    mutationFn: () => usersApi.updateMyProfile(changes),
    onSuccess: () => { setChanges({}); void qc.invalidateQueries({ queryKey: ['me'] }); },
  });

  if (me.isLoading || !me.data) return <Skeleton height={200} />;
  const p = me.data.profile;
  const set = <K extends keyof UpdateProfileRequest>(k: K, v: UpdateProfileRequest[K]) => setChanges((c) => ({ ...c, [k]: v }));
  const onSubmit = (e: FormEvent) => { e.preventDefault(); save.mutate(); };
  const dirty = Object.keys(changes).length > 0;

  return (
    <div className="cq-page">
      <h1>Mi perfil</h1>
      <form onSubmit={onSubmit}>
        <Card header="Datos personales" footer={<span>Tu RUT y correo nunca se muestran a otros jugadores.</span>}>
          <div className="cq-form">
            <label>Nombre<input required defaultValue={p.firstName} onChange={(e) => set('firstName', e.target.value)} /></label>
            <label>Apellido<input required defaultValue={p.lastName} onChange={(e) => set('lastName', e.target.value)} /></label>
            <label>Nombre visible<input defaultValue={p.displayName ?? ''} onChange={(e) => set('displayName', e.target.value)} /></label>
            <label>RUT<input defaultValue={p.rut ?? ''} placeholder="12345678-9" onChange={(e) => set('rut', e.target.value)} /></label>
            <label>Fecha de nacimiento<input type="date" defaultValue={p.birthDate ?? ''} onChange={(e) => set('birthDate', e.target.value)} /></label>
            <label>Género
              <select defaultValue={p.gender ?? ''} onChange={(e) => set('gender', e.target.value)}>
                <option value="">Prefiero no decir</option><option value="F">Femenino</option><option value="M">Masculino</option><option value="O">Otro</option>
              </select>
            </label>
            <label>Región<input defaultValue={p.region ?? ''} onChange={(e) => set('region', e.target.value)} /></label>
            <label>País
              <select defaultValue={p.country?.id ?? ''} onChange={(e) => set('countryId', Number(e.target.value) || undefined)}>
                <option value="">—</option>
                {(countries.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
            <label>Club federativo
              <select defaultValue={p.club?.id ?? ''} onChange={(e) => set('clubId', Number(e.target.value) || undefined)}>
                <option value="">—</option>
                {(clubs.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
          </div>
        </Card>
        <Card header="Cuentas externas" style={{ marginTop: 16 }}>
          <div className="cq-form">
            <label>Usuario de Lichess<input defaultValue={p.lichessUsername ?? ''} onChange={(e) => set('lichessUsername', e.target.value)} /></label>
            <label>Usuario de Chess.com<input defaultValue={p.chesscomUsername ?? ''} onChange={(e) => set('chesscomUsername', e.target.value)} /></label>
          </div>
        </Card>
        <div className="cq-actions" style={{ marginTop: 16 }}>
          <Button type="submit" disabled={!dirty} loading={save.isPending}>Guardar cambios</Button>
          <StatusMessage error={save.error} success={save.isSuccess && !dirty ? 'Perfil guardado' : null} />
        </div>
      </form>
    </div>
  );
};
