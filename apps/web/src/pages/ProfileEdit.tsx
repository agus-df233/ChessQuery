import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, Skeleton } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { usersApi } from '../api/users';
import type { Profile, UpdateProfileRequest } from '../api/types';
import { StatusMessage } from '../components/StatusMessage';

type SetField = <K extends keyof UpdateProfileRequest>(k: K, v: UpdateProfileRequest[K]) => void;
type SectionProps = { profile: Profile; set: SetField };

/** Campo de texto que solo informa cambios; el valor inicial viene del perfil. */
const TextField = ({ label, field, initial, set, required, placeholder }: {
  label: string; field: 'firstName' | 'lastName' | 'displayName' | 'rut' | 'region' | 'lichessUsername' | 'chesscomUsername';
  initial: string | null; set: SetField; required?: boolean; placeholder?: string;
}) => (
  <label>{label}
    <input required={required} placeholder={placeholder} defaultValue={initial ?? ''}
           onChange={(e) => set(field, e.target.value)} />
  </label>
);

const PersonalData = ({ profile: p, set }: SectionProps) => (
  <>
    <TextField label="Nombre" field="firstName" initial={p.firstName} set={set} required />
    <TextField label="Apellido" field="lastName" initial={p.lastName} set={set} required />
    <TextField label="Nombre visible" field="displayName" initial={p.displayName} set={set} />
    <TextField label="RUT" field="rut" initial={p.rut} set={set} placeholder="12345678-9" />
    <label>Fecha de nacimiento
      <input type="date" defaultValue={p.birthDate ?? ''} onChange={(e) => set('birthDate', e.target.value)} />
    </label>
    <label>Género
      <select defaultValue={p.gender ?? ''} onChange={(e) => set('gender', e.target.value)}>
        <option value="">Prefiero no decir</option><option value="F">Femenino</option>
        <option value="M">Masculino</option><option value="O">Otro</option>
      </select>
    </label>
    <TextField label="Región" field="region" initial={p.region} set={set} />
  </>
);

/** Select de un catálogo (países o clubes); "—" deja el campo sin valor. */
const CatalogSelect = ({ label, options, initial, onPick }: {
  label: string; options: { id: number; name: string }[] | undefined; initial: number | undefined; onPick: (id?: number) => void;
}) => (
  <label>{label}
    <select defaultValue={initial ?? ''} onChange={(e) => onPick(Number(e.target.value) || undefined)}>
      <option value="">—</option>
      {(options ?? []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
    </select>
  </label>
);

/** País y club federativo vienen del catálogo (se cargan una vez y no cambian durante la sesión). */
const CatalogFields = ({ profile: p, set }: SectionProps) => {
  const countries = useQuery({ queryKey: ['countries'], queryFn: usersApi.countries, staleTime: Infinity });
  const clubs = useQuery({ queryKey: ['clubs'], queryFn: usersApi.clubs, staleTime: Infinity });
  return (
    <>
      <CatalogSelect label="País" options={countries.data} initial={p.country?.id} onPick={(id) => set('countryId', id)} />
      <CatalogSelect label="Club federativo" options={clubs.data} initial={p.club?.id} onPick={(id) => set('clubId', id)} />
    </>
  );
};

const LinkedAccounts = ({ profile: p, set }: SectionProps) => (
  <>
    <TextField label="Usuario de Lichess" field="lichessUsername" initial={p.lichessUsername} set={set} />
    <TextField label="Usuario de Chess.com" field="chesscomUsername" initial={p.chesscomUsername} set={set} />
  </>
);

/** Edición del propio perfil. Solo se envían los campos que el usuario tocó. */
export const ProfileEdit = () => {
  const qc = useQueryClient();
  const me = useMe();
  const [changes, setChanges] = useState<UpdateProfileRequest>({});
  const save = useMutation({
    mutationFn: () => usersApi.updateMyProfile(changes),
    onSuccess: () => { setChanges({}); void qc.invalidateQueries({ queryKey: ['me'] }); },
  });

  if (me.isLoading || !me.data) return <Skeleton height={200} />;
  const profile = me.data.profile;
  const set: SetField = (k, v) => setChanges((c) => ({ ...c, [k]: v }));
  const onSubmit = (e: FormEvent) => { e.preventDefault(); save.mutate(); };
  const dirty = Object.keys(changes).length > 0;

  return (
    <div className="cq-page">
      <h1>Mi perfil</h1>
      <form onSubmit={onSubmit}>
        <Card header="Datos personales" footer={<span>Tu RUT y correo nunca se muestran a otros jugadores.</span>}>
          <div className="cq-form">
            <PersonalData profile={profile} set={set} />
            <CatalogFields profile={profile} set={set} />
          </div>
        </Card>
        <Card header="Cuentas externas" style={{ marginTop: 16 }}>
          <div className="cq-form"><LinkedAccounts profile={profile} set={set} /></div>
        </Card>
        <div className="cq-actions" style={{ marginTop: 16 }}>
          <Button type="submit" disabled={!dirty} loading={save.isPending}>Guardar cambios</Button>
          <StatusMessage error={save.error} success={save.isSuccess && !dirty ? 'Perfil guardado' : null} />
        </div>
      </form>
    </div>
  );
};
