import { ChangeEvent, FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, ErrorAlert, Skeleton, Table, type TableColumn } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';
import { organizationsApi } from '../api/users';
import type { Organization, OrganizationRequest, Profile, RosterCreateRequest } from '../api/types';
import { parseRosterCsv, type RosterCsvRow } from '../lib/rosterCsv';
import { StatusMessage } from '../components/StatusMessage';

/** Formulario de creación/edición del club. */
const ClubForm = ({ initial, onSubmit, pending, error, submitLabel }: {
  initial?: Organization; onSubmit: (b: OrganizationRequest) => void; pending: boolean; error: unknown; submitLabel: string;
}) => {
  const [form, setForm] = useState<OrganizationRequest>({
    name: initial?.name ?? '', city: initial?.city ?? '', description: initial?.description ?? '',
  });
  return (
    <form onSubmit={(e: FormEvent) => { e.preventDefault(); onSubmit(form); }}>
      <div className="cq-form">
        <label>Nombre del club<input required maxLength={150} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
        <label>Ciudad<input maxLength={120} value={form.city ?? ''} onChange={(e) => setForm({ ...form, city: e.target.value })} /></label>
        <label style={{ gridColumn: '1 / -1' }}>Descripción<textarea maxLength={500} rows={3} value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
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

/** Panel del club: datos, plan y roster provisorio con carga por CSV. */
export const ClubPanel = () => {
  const qc = useQueryClient();
  const org = useQuery({ queryKey: ['org'], queryFn: organizationsApi.mine });
  const roster = useQuery({ queryKey: ['roster'], queryFn: organizationsApi.roster });
  const refresh = () => { void qc.invalidateQueries({ queryKey: ['org'] }); void qc.invalidateQueries({ queryKey: ['roster'] }); };
  const update = useMutation({ mutationFn: organizationsApi.update, onSuccess: refresh });
  const add = useMutation({ mutationFn: organizationsApi.addToRoster, onSuccess: refresh });
  const deactivate = useMutation({ mutationFn: organizationsApi.deactivate, onSuccess: refresh });
  const tags = useMutation({ mutationFn: (v: { id: number; tags: string[] }) => organizationsApi.updateTags(v.id, v.tags), onSuccess: refresh });
  const [preview, setPreview] = useState<RosterCsvRow[] | null>(null);
  const [importing, setImporting] = useState<{ done: number; failed: number } | null>(null);
  const [newPlayer, setNewPlayer] = useState<RosterCreateRequest>({ firstName: '', lastName: '' });

  if (org.isLoading || roster.isLoading) return <Skeleton height={200} />;
  if (org.error || !org.data) return <ErrorAlert message="No pudimos cargar tu club" onRetry={() => void org.refetch()} />;
  const o = org.data;
  const active = (roster.data ?? []).filter((p) => p.active);

  const onCsv = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setPreview(parseRosterCsv(await file.text(), roster.data ?? []));
    setImporting(null);
  };
  const importRows = async () => {
    const ok = (preview ?? []).filter((r) => r.status === 'ok' && r.input);
    let done = 0, failed = 0;
    for (const r of ok) {
      try { await organizationsApi.addToRoster(r.input!); done++; } catch { failed++; }
    }
    setImporting({ done, failed });
    setPreview(null);
    refresh();
  };
  const columns: TableColumn<Profile>[] = [
    { key: 'name', header: 'Jugador', render: (p) => `${p.firstName} ${p.lastName}` },
    { key: 'rut', header: 'RUT', render: (p) => p.rut ?? '—' },
    { key: 'elo', header: 'ELO', align: 'right', render: (p) => p.ratings.national ?? '—' },
    { key: 'tags', header: 'Etiquetas', render: (p) => (
      <div className="cq-tags">
        {p.tags.map((t) => <Badge key={t}>{t}</Badge>)}
        <button type="button" className="cq-input" style={{ minHeight: 28, padding: '2px 8px' }} aria-label={`Editar etiquetas de ${p.firstName} ${p.lastName}`}
          onClick={() => { const v = window.prompt('Etiquetas separadas por coma', p.tags.join(', ')); if (v !== null) tags.mutate({ id: p.id, tags: v.split(',') }); }}>✎</button>
      </div>
    )},
    { key: 'acc', header: 'Cuenta', render: (p) => p.provisional ? <Badge variant="warning">Provisorio</Badge> : <Badge variant="success">Con cuenta</Badge> },
    { key: 'act', header: 'Acciones', render: (p) => <Button size="sm" variant="secondary" onClick={() => deactivate.mutate(p.id)} aria-label={`Dar de baja a ${p.firstName} ${p.lastName}`}>Baja</Button> },
  ];

  return (
    <div className="cq-page">
      <h1>{o.name}</h1>
      <div className="cq-actions">
        <Badge variant={o.plan === 'PRO' ? 'gold' : 'neutral'}>Plan {o.plan}</Badge>
        <span className="cq-muted">Roster {o.rosterCount}/{o.maxRosterPlayers} · Torneos activos máx. {o.maxActiveTournaments}</span>
      </div>
      <Card header="Datos del club"><ClubForm initial={o} onSubmit={(b) => update.mutate(b)} pending={update.isPending} error={update.error} submitLabel="Guardar" /></Card>

      <Card header="Agregar jugador al roster">
        <form onSubmit={(e: FormEvent) => { e.preventDefault(); add.mutate(newPlayer, { onSuccess: () => setNewPlayer({ firstName: '', lastName: '' }) }); }}>
          <div className="cq-form">
            <label>Nombre<input required value={newPlayer.firstName} onChange={(e) => setNewPlayer({ ...newPlayer, firstName: e.target.value })} /></label>
            <label>Apellido<input required value={newPlayer.lastName} onChange={(e) => setNewPlayer({ ...newPlayer, lastName: e.target.value })} /></label>
            <label>RUT<input value={newPlayer.rut ?? ''} onChange={(e) => setNewPlayer({ ...newPlayer, rut: e.target.value || undefined })} /></label>
            <label>Email<input type="email" value={newPlayer.email ?? ''} onChange={(e) => setNewPlayer({ ...newPlayer, email: e.target.value || undefined })} /></label>
            <label>ELO nacional<input type="number" min={0} value={newPlayer.eloNational ?? ''} onChange={(e) => setNewPlayer({ ...newPlayer, eloNational: Number(e.target.value) || undefined })} /></label>
          </div>
          <div className="cq-actions" style={{ marginTop: 12 }}>
            <Button type="submit" loading={add.isPending}>Agregar</Button>
            <StatusMessage error={add.error} />
          </div>
        </form>
      </Card>

      <Card header="Carga masiva (CSV: nombre, apellido, email, rut, elo)">
        <input type="file" accept=".csv,text/csv" aria-label="Archivo CSV del roster" onChange={(e) => void onCsv(e)} />
        {preview && (
          <div style={{ marginTop: 12 }}>
            <p className="cq-muted">
              {preview.filter((r) => r.status === 'ok').length} para importar · {preview.filter((r) => r.status === 'duplicate').length} duplicados · {preview.filter((r) => r.status === 'error').length} con error
            </p>
            <ul className="cq-csv-preview">
              {preview.map((r) => <li key={r.line}>Línea {r.line}: {r.status === 'ok' ? `${r.input!.firstName} ${r.input!.lastName}` : r.reason}</li>)}
            </ul>
            <Button onClick={() => void importRows()} disabled={!preview.some((r) => r.status === 'ok')}>Importar</Button>
          </div>
        )}
        {importing && <StatusMessage success={`Importados ${importing.done}` + (importing.failed ? `, fallaron ${importing.failed}` : '')} />}
      </Card>

      <Card header={`Roster (${active.length} activos)`} padded={false}>
        <Table columns={columns} rows={active} rowKey={(p) => p.id} emptyMessage="Tu roster está vacío." />
        <StatusMessage error={deactivate.error ?? tags.error} />
      </Card>
    </div>
  );
};

/** Punto de entrada de /club: crea o administra según el estado del usuario. */
export const Club = () => {
  const me = useMe();
  if (me.isLoading) return <Skeleton height={200} />;
  return me.data?.organizer ? <ClubPanel /> : <CreateClub />;
};
