import { Badge, Card, Table, type TableColumn } from '@chessquery/ui-lib';
import type { EntryView, RoundView, StandingView, TournamentView } from '../../api/tournamentTypes';
import { STATUS_BADGE, STATUS_LABEL, summary } from './labels';

const name = (p: { name: string; title: string | null }) => (p.title ? `${p.title} ${p.name}` : p.name);

export const TournamentHeader = ({ t }: { t: TournamentView }) => (
  <div>
    <h1 style={{ marginBottom: 4 }}>{t.name}</h1>
    <div className="cq-actions">
      <Badge variant={STATUS_BADGE[t.status]}>{STATUS_LABEL[t.status]}</Badge>
      {t.currentRound > 0 && <span className="cq-muted">Ronda {t.currentRound} de {t.roundsPlanned}</span>}
      {t.rated && <Badge variant="gold">Válido para rating</Badge>}
    </div>
    <p className="cq-muted">{summary(t)}</p>
  </div>
);

/** Tabla con desempates: Buchholz corte 1, Buchholz, Sonneborn-Berger y victorias. */
export const StandingsCard = ({ rows }: { rows: StandingView[] }) => {
  const columns: TableColumn<StandingView>[] = [
    { key: 'pos', header: '#', width: 44, render: (r) => r.position },
    { key: 'name', header: 'Jugador', render: (r) => name(r.player) },
    { key: 'pts', header: 'Pts', align: 'right', render: (r) => <strong>{r.points.toFixed(1)}</strong> },
    { key: 'bc1', header: 'Bu-1', align: 'right', render: (r) => r.buchholzCut1.toFixed(1) },
    { key: 'bu', header: 'Bu', align: 'right', render: (r) => r.buchholz.toFixed(1) },
    { key: 'sb', header: 'SB', align: 'right', render: (r) => r.sonnebornBerger.toFixed(2) },
    { key: 'w', header: 'V', align: 'right', render: (r) => r.wins },
  ];
  return (
    <Card header="Clasificación" padded={false}>
      <Table columns={columns} rows={rows} rowKey={(r) => r.player.playerId} emptyMessage="Aún no hay resultados." />
      <p className="cq-muted" style={{ padding: '8px 16px', margin: 0 }}>
        Bu-1: Buchholz sin el peor rival · Bu: Buchholz · SB: Sonneborn-Berger · V: victorias
      </p>
    </Card>
  );
};

export const PlayersCard = ({ players }: { players: EntryView[] }) => {
  const columns: TableColumn<EntryView>[] = [
    { key: 'n', header: 'N°', width: 44, render: (e) => e.startRank },
    { key: 'name', header: 'Jugador', render: (e) => name(e.player) },
    { key: 'club', header: 'Club', render: (e) => e.clubName ?? '—' },
    { key: 'elo', header: 'Rating', align: 'right', render: (e) => e.player.rating ?? '—' },
  ];
  return (
    <Card header={`Inscritos (${players.length})`} padded={false}>
      <Table columns={columns} rows={players} rowKey={(e) => e.player.playerId} emptyMessage="Todavía no hay inscritos." />
    </Card>
  );
};

/** Mesas de una ronda. {@code renderResult} permite al organizador poner un selector en vez del texto. */
export const RoundCard = ({ round, renderResult }: {
  round: RoundView; renderResult?: (board: RoundView['boards'][number]) => React.ReactNode;
}) => {
  const columns: TableColumn<RoundView['boards'][number]>[] = [
    { key: 'b', header: 'Mesa', width: 56, render: (b) => b.board },
    { key: 'w', header: 'Blancas', render: (b) => name(b.white) },
    { key: 'r', header: 'Resultado', align: 'center', render: (b) => (renderResult && b.black ? renderResult(b) : b.resultLabel ?? 'pendiente') },
    { key: 'n', header: 'Negras', render: (b) => (b.black ? name(b.black) : 'descansa (bye)') },
  ];
  return (
    <Card header={`Ronda ${round.number}${round.complete ? '' : ' · en juego'}`} padded={false}>
      <Table columns={columns} rows={round.boards} rowKey={(b) => b.board} />
    </Card>
  );
};
