import { ChangeEvent, FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, ErrorAlert, Skeleton, Table, type TableColumn } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { organizationsApi } from '../api/users';
import type { Organization, OrganizationRequest, Profile, RosterCreateRequest } from '../api/types';
import { parseRosterCsv, type RosterCsvRow } from '../lib/rosterCsv';
import { StatusMessage } from '../components/StatusMessage';

const EMPTY_CLUB: OrganizationRequest = { name: '', city: '', description: '' };

/** Valores iniciales del formulario: los del club existente (sin nulls) o vacíos al crear. */
const initialClubForm = (o?: Organization): OrganizationRequest =>
  o ? { name: o.name, city: o.city ?? '', description: o.description ?? '' } : EMPTY_CLUB;

/** Formulario de creación/edición del club. */
const ClubForm = ({ initial, onSubmit, pending, error, submitLabel }: {
  initial?: Organization; onSubmit: (b: OrganizationRequest) => void; pending: boolean; error: unknown; submitLabel: string;
}) => {
  const [form, setForm] = useState<OrganizationRequest>(() => initialClubForm(initial));
  const set = (patch: Partial<OrganizationRequest>) => setForm({ ...form, ...patch });
  return (
    <form onSubmit={(e: FormEvent) => { e.preventDefault(); onSubmit(form); }}>
      <div className="cq-form">
        <label>Nombre del club<input required maxLength={150} value={form.name} onChange={(e) => set({ name: e.target.value })} /></label>
        <label>Ciudad<input maxLength={120} value={form.city ?? ''} onChange={(e) => set({ city: e.target.value })} /></label>
        <label style={{ gridColumn: '1 / -1' }}>Descripción<textarea maxLength={500} rows={3} value={form.description ?? ''} onChange={(e) => set({ description: e.target.value })} /></label>
      </div>
      <div className="cq-actions" style={{ marginTop: 12 }}>
        <Button type="submit" loading={pending}>{submitLabel}</Button>
        <StatusMessage error={error} />
      </div>
    </form>
  );
};

/** Sin club todavía: crearlo convierte al jugador en organizador. */
export const CreateClub = () => {
  const qc = useQueryClient();
  const create = useMutation({ mutationFn: organizationsApi.create, onSuccess: () => void qc.invalidateQueries({ queryKey: ['me'] }) });
  return (
    <div className="cq-page">
      <h1>Crear mi club</h1>
      <p className="cq-muted">Con tu club podrás cargar a tus jugadores y organizar torneos. Empiezas en el plan gratuito.</p>
      <Card><ClubForm onSubmit={(b) => create.mutate(b)} pending={create.isPending} error={create.error} submitLabel="Crear club" /></Card>
    </div>
  );
};

/** Alta individual de un jugador provisorio en el roster. */
const AddPlayerCard = ({ onAdded }: { onAdded: () => void }) => {
  const add = useMutation({ mutationFn: organizationsApi.addToRoster, onSuccess: onAdded });
  const [player, setPlayer] = useState<RosterCreateRequest>({ firstName: '', lastName: '' });
  const set = (patch: Partial<RosterCreateRequest>) => setPlayer({ ...player, ...patch });
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    add.mutate(player, { onSuccess: () => setPlayer({ firstName: '', lastName: '' }) });
  };
  return (
    <Card header="Agregar jugador al roster">
      <form onSubmit={onSubmit}>
        <div className="cq-form">
          <label>Nombre<input required value={player.firstName} onChange={(e) => set({ firstName: e.target.value })} /></label>
          <label>Apellido<input required value={player.lastName} onChange={(e) => set({ lastName: e.target.value })} /></label>
          <label>RUT<input value={player.rut ?? ''} onChange={(e) => set({ rut: e.target.value || undefined })} /></label>
          <label>Email<input type="email" value={player.email ?? ''} onChange={(e) => set({ email: e.target.value || undefined })} /></label>
          <label>ELO nacional<input type="number" min={0} value={player.eloNational ?? ''} onChange={(e) => set({ eloNational: Number(e.target.value) || undefined })} /></label>
        </div>
        <div className="cq-actions" style={{ marginTop: 12 }}>
          <Button type="submit" loading={add.isPending}>Agregar</Button>
          <StatusMessage error={add.error} />
        </div>
      </form>
    </Card>
  );
};

const countBy = (rows: RosterCsvRow[], status: RosterCsvRow['status']) => rows.filter((r) => r.status === status).length;

/** Carga masiva: vista previa fila a fila (ok / duplicado / error) y luego importación de las válidas. */
const CsvImportCard = ({ roster, onImported }: { roster: Profile[]; onImported: () => void }) => {
  const [preview, setPreview] = useState<RosterCsvRow[] | null>(null);
  const [result, setResult] = useState<{ done: number; failed: number } | null>(null);

  const onFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setPreview(parseRosterCsv(await file.text(), roster));
    setResult(null);
  };
  const importRows = async () => {
    let done = 0, failed = 0;
    for (const row of (preview ?? []).filter((r) => r.status === 'ok' && r.input)) {
      try { await organizationsApi.addToRoster(row.input!); done++; } catch { failed++; }
    }
    setResult({ done, failed });
    setPreview(null);
    onImported();
  };
  return (
    <Card header="Carga masiva (CSV: nombre, apellido, email, rut, elo)">
      <input type="file" accept=".csv,text/csv" aria-label="Archivo CSV del roster" onChange={(e) => void onFile(e)} />
      {preview && (
        <div style={{ marginTop: 12 }}>
          <p className="cq-muted">
            {countBy(preview, 'ok')} para importar · {countBy(preview, 'duplicate')} duplicados · {countBy(preview, 'error')} con error
          </p>
          <ul className="cq-csv-preview">
            {preview.map((r) => <li key={r.line}>Línea {r.line}: {r.status === 'ok' ? `${r.input!.firstName} ${r.input!.lastName}` : r.reason}</li>)}
          </ul>
          <Button onClick={() => void importRows()} disabled={countBy(preview, 'ok') === 0}>Importar</Button>
        </div>
      )}
      {result && <StatusMessage success={`Importados ${result.done}` + (result.failed ? `, fallaron ${result.failed}` : '')} />}
    </Card>
  );
};

/** Roster activo con etiquetas editables y baja lógica. */
const RosterTable = ({ active, onChanged }: { active: Profile[]; onChanged: () => void }) => {
  const deactivate = useMutation({ mutationFn: organizationsApi.deactivate, onSuccess: onChanged });
  const tags = useMutation({ mutationFn: (v: { id: number; tags: string[] }) => organizationsApi.updateTags(v.id, v.tags), onSuccess: onChanged });
  const editTags = (p: Profile) => {
    const value = window.prompt('Etiquetas separadas por coma', p.tags.join(', '));
    if (value !== null) tags.mutate({ id: p.id, tags: value.split(',') });
  };
  const columns: TableColumn<Profile>[] = [
    { key: 'name', header: 'Jugador', render: (p) => `${p.firstName} ${p.lastName}` },
    { key: 'rut', header: 'RUT', render: (p) => p.rut ?? '—' },
    { key: 'elo', header: 'ELO', align: 'right', render: (p) => p.ratings.national ?? '—' },
    { key: 'tags', header: 'Etiquetas', render: (p) => (
      <div className="cq-tags">
        {p.tags.map((t) => <Badge key={t}>{t}</Badge>)}
        <button type="button" className="cq-input" style={{ minHeight: 28, padding: '2px 8px' }}
          aria-label={`Editar etiquetas de ${p.firstName} ${p.lastName}`} onClick={() => editTags(p)}>✎</button>
      </div>
    )},
    { key: 'acc', header: 'Cuenta', render: (p) => p.provisional ? <Badge variant="warning">Provisorio</Badge> : <Badge variant="success">Con cuenta</Badge> },
    { key: 'act', header: 'Acciones', render: (p) => <Button size="sm" variant="secondary" onClick={() => deactivate.mutate(p.id)} aria-label={`Dar de baja a ${p.firstName} ${p.lastName}`}>Baja</Button> },
  ];
  return (
    <Card header={`Roster (${active.length} activos)`} padded={false}>
      <Table columns={columns} rows={active} rowKey={(p) => p.id} emptyMessage="Tu roster está vacío." />
      <StatusMessage error={deactivate.error ?? tags.error} />
    </Card>
  );
};

/** Panel del club: datos, plan y roster provisorio con carga por CSV. */
export const ClubPanel = () => {
  const qc = useQueryClient();
  const org = useQuery({ queryKey: ['org'], queryFn: organizationsApi.mine });
  const roster = useQuery({ queryKey: ['roster'], queryFn: organizationsApi.roster });
  const refresh = () => { void qc.invalidateQueries({ queryKey: ['org'] }); void qc.invalidateQueries({ queryKey: ['roster'] }); };
  const update = useMutation({ mutationFn: organizationsApi.update, onSuccess: refresh });

  if (org.isLoading || roster.isLoading) return <Skeleton height={200} />;
  if (org.error || !org.data) return <ErrorAlert message="No pudimos cargar tu club" onRetry={() => void org.refetch()} />;
  const o = org.data;
  const all = roster.data ?? [];
  return (
    <div className="cq-page">
      <h1>{o.name}</h1>
      <div className="cq-actions">
        <Badge variant={o.plan === 'PRO' ? 'gold' : 'neutral'}>Plan {o.plan}</Badge>
        <span className="cq-muted">Roster {o.rosterCount}/{o.maxRosterPlayers} · Torneos activos máx. {o.maxActiveTournaments}</span>
      </div>
      <Card header="Datos del club"><ClubForm initial={o} onSubmit={(b) => update.mutate(b)} pending={update.isPending} error={update.error} submitLabel="Guardar" /></Card>
      <AddPlayerCard onAdded={refresh} />
      <CsvImportCard roster={all} onImported={refresh} />
      <RosterTable active={all.filter((p) => p.active)} onChanged={refresh} />
    </div>
  );
};

/** Punto de entrada de /club: crea o administra según el estado del usuario. */
export const Club = () => {
  const me = useMe();
  if (me.isLoading) return <Skeleton height={200} />;
  return me.data?.organizer ? <ClubPanel /> : <CreateClub />;
};
