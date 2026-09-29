import { FormEvent, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card } from '@chessquery/ui-lib';
import { tournamentsApi } from '../api/tournaments';
import { organizationsApi } from '../api/users';
import type { GameResult, RoundView, TournamentDetail, TournamentRequest } from '../api/tournamentTypes';
import { StatusMessage } from '../components/StatusMessage';
import { RESULT_OPTIONS } from '../components/tournament/labels';
import { TournamentDetailView, tournamentKeys } from '../components/tournament/TournamentDetailView';
import { TournamentList } from './PublicTournaments';

const today = () => new Date().toISOString().slice(0, 10);
const EMPTY: TournamentRequest = { name: '', city: '', startDate: today(), format: 'SWISS', rounds: 5, timeControl: '60+30', rated: true };

const TournamentForm = ({ onCreated }: { onCreated: (id: number) => void }) => {
  const qc = useQueryClient();
  const [form, setForm] = useState<TournamentRequest>(EMPTY);
  const set = (patch: Partial<TournamentRequest>) => setForm({ ...form, ...patch });
  const create = useMutation({
    mutationFn: tournamentsApi.create,
    onSuccess: (d) => { void qc.invalidateQueries({ queryKey: ['my-tournaments'] }); onCreated(d.tournament.id); },
  });
  const onSubmit = (e: FormEvent) => { e.preventDefault(); create.mutate(form); };
  return (
    <form onSubmit={onSubmit}>
      <div className="cq-form">
        <label>Nombre del torneo<input required maxLength={160} value={form.name} onChange={(e) => set({ name: e.target.value })} /></label>
        <label>Ciudad<input maxLength={100} value={form.city ?? ''} onChange={(e) => set({ city: e.target.value })} /></label>
        <label>Fecha de inicio<input type="date" required value={form.startDate} onChange={(e) => set({ startDate: e.target.value })} /></label>
        <label>Sistema
          <select value={form.format} onChange={(e) => set({ format: e.target.value as TournamentRequest['format'] })}>
            <option value="SWISS">Suizo</option><option value="ROUND_ROBIN">Todos contra todos</option>
          </select>
        </label>
        <label>Rondas{form.format === 'ROUND_ROBIN' ? ' (se calculan solas)' : ''}
          <input type="number" min={1} max={30} required value={form.rounds} disabled={form.format === 'ROUND_ROBIN'}
                 onChange={(e) => set({ rounds: Number(e.target.value) })} />
        </label>
        <label>Ritmo de juego<input maxLength={40} value={form.timeControl ?? ''} onChange={(e) => set({ timeControl: e.target.value })} placeholder="60+30" /></label>
        <label className="cq-check">
          <input type="checkbox" checked={form.rated} onChange={(e) => set({ rated: e.target.checked })} /> Válido para rating de ChessQuery
        </label>
      </div>
      <div className="cq-actions" style={{ marginTop: 12 }}>
        <Button type="submit" loading={create.isPending}>Crear torneo</Button>
        <StatusMessage error={create.error} />
      </div>
    </form>
  );
};

/** /club/torneos: torneos del club y creación de uno nuevo. */
export const OrganizerTournaments = () => {
  const navigate = useNavigate();
  const mine = useQuery({ queryKey: ['my-tournaments'], queryFn: tournamentsApi.mine });
  return (
    <div className="cq-page">
      <p><Link to="/club">← Mi club</Link></p>
      <h1>Torneos del club</h1>
      <Card header="Nuevo torneo"><TournamentForm onCreated={(id) => navigate(`/club/torneos/${id}`)} /></Card>
      <Card header="Mis torneos">
        <TournamentList items={mine.data?.organized} base="/club/torneos" empty="Todavía no creas torneos" />
      </Card>
    </div>
  );
};

const useTournamentMutation = <T,>(id: number, fn: (arg: T) => Promise<unknown>) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      tournamentKeys(id).forEach((queryKey) => void qc.invalidateQueries({ queryKey }));
      void qc.invalidateQueries({ queryKey: ['my-tournaments'] });
    },
  });
};

/** Inscribir jugadores del roster del club (con o sin cuenta). */
const RegisterFromRoster = ({ d }: { d: TournamentDetail }) => {
  const roster = useQuery({ queryKey: ['roster'], queryFn: organizationsApi.roster });
  const register = useTournamentMutation(d.tournament.id, (playerId: number) => tournamentsApi.register(d.tournament.id, playerId));
  const inTournament = new Set(d.players.map((p) => p.player.playerId));
  const available = (roster.data ?? []).filter((p) => p.active && !inTournament.has(p.id));
  const [picked, setPicked] = useState('');
  const [bulk, setBulk] = useState<string | null>(null);
  /** Uno por uno (el servidor valida cada inscripción); informa cuántas fallaron en vez de cortar en la primera. */
  const registerAll = async () => {
    const results = await Promise.allSettled(available.map((p) => register.mutateAsync(p.id)));
    const failed = results.filter((r) => r.status === 'rejected').length;
    setBulk(`Inscritos ${results.length - failed}` + (failed ? `, fallaron ${failed}` : ''));
  };
  return (
    <div className="cq-actions">
      <label>Inscribir del roster
        <select value={picked} onChange={(e) => setPicked(e.target.value)} aria-label="Jugador del roster">
          <option value="">Elige un jugador…</option>
          {available.map((p) => <option key={p.id} value={p.id}>{p.firstName} {p.lastName}</option>)}
        </select>
      </label>
      <Button size="sm" disabled={!picked} loading={register.isPending}
              onClick={() => register.mutate(Number(picked), { onSuccess: () => setPicked('') })}>Inscribir</Button>
      <Button size="sm" variant="secondary" disabled={available.length === 0} loading={register.isPending}
              onClick={() => void registerAll()}>Inscribir a todo el roster</Button>
      <StatusMessage error={register.error} success={bulk} />
    </div>
  );
};

const downloadTrf = async (id: number) => {
  const text = await tournamentsApi.trf(id);
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
  const a = Object.assign(document.createElement('a'), { href: url, download: `torneo-${id}.trf` });
  a.click();
  URL.revokeObjectURL(url);
};

/** Generar la siguiente ronda y cerrar el torneo, según el estado. */
const RoundButtons = ({ t }: { t: TournamentDetail['tournament'] }) => {
  const next = useTournamentMutation(t.id, () => tournamentsApi.nextRound(t.id));
  const finish = useTournamentMutation(t.id, () => tournamentsApi.finish(t.id));
  const canPlayMore = t.status === 'OPEN' || t.currentRound < t.roundsPlanned;
  const nextLabel = t.status === 'OPEN' ? 'Cerrar inscripciones y generar ronda 1' : `Generar ronda ${t.currentRound + 1}`;
  return (
    <>
      {t.status !== 'FINISHED' && canPlayMore && <Button loading={next.isPending} onClick={() => next.mutate(undefined)}>{nextLabel}</Button>}
      {t.status === 'IN_PROGRESS' && (
        <Button variant={canPlayMore ? 'secondary' : 'primary'} loading={finish.isPending} onClick={() => finish.mutate(undefined)}>
          Cerrar torneo
        </Button>
      )}
      <StatusMessage error={next.error ?? finish.error} success={finish.isSuccess ? 'Torneo cerrado: ratings enviados' : null} />
    </>
  );
};

/** Acciones del organizador: inscribir (abierto), rondas, exportar TRF y la vista pública para la sala. */
const OrganizerActions = ({ d }: { d: TournamentDetail }) => {
  const t = d.tournament;
  return (
    <Card header="Dirección del torneo">
      {t.status === 'OPEN' && <RegisterFromRoster d={d} />}
      <div className="cq-actions" style={{ marginTop: 12 }}>
        <RoundButtons t={t} />
        {t.status !== 'OPEN' && <Button variant="secondary" onClick={() => void downloadTrf(t.id)}>Exportar TRF</Button>}
        <Link to={`/torneos/${t.id}`} target="_blank">Vista pública para la sala ↗</Link>
      </div>
    </Card>
  );
};

/** Selector de resultado de una mesa de la ronda en curso. */
const ResultSelect = ({ id, round, board }: { id: number; round: number; board: RoundView['boards'][number] }) => {
  const save = useTournamentMutation(id, (result: GameResult) => tournamentsApi.setResult(id, round, board.board, result));
  return (
    <select className="cq-result-select" aria-label={`Resultado mesa ${board.board}`} value={board.result ?? ''} disabled={save.isPending}
            onChange={(e) => save.mutate(e.target.value as GameResult)}>
      <option value="" disabled>pendiente</option>
      {RESULT_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
    </select>
  );
};

/** /club/torneos/:id: el organizador dirige el torneo sobre la misma vista que ve la sala. */
export const OrganizerTournament = () => {
  const id = Number(useParams().id);
  return (
    <>
      <p style={{ padding: '0 16px' }}><Link to="/club/torneos">← Torneos del club</Link></p>
      <TournamentDetailView id={id} actions={(d) => <OrganizerActions d={d} />}
        renderResult={(round) => (b) => <ResultSelect id={id} round={round.number} board={b} />} />
    </>
  );
};
