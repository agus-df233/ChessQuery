import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Button, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import type { LiveView } from '../api/tournamentTypes';
import { QrCode } from '../components/QrCode';
import { useLiveTournament } from '../components/tournament/useLiveTournament';

const ROTATE_MS = 15_000;
const ROWS_PER_PAGE = 12;

type Panel = { kind: 'boards' | 'standings'; page: number };

/** Paneles que rotan: emparejamientos de la ronda en curso y la clasificación, de a 12 filas cada uno. */
export const panelsOf = (live: LiveView): Panel[] => {
  const boards = live.rounds.at(-1)?.boards.length ?? 0;
  const pages = (n: number) => Math.max(1, Math.ceil(n / ROWS_PER_PAGE));
  const list: Panel[] = [];
  if (boards) for (let p = 0; p < pages(boards); p++) list.push({ kind: 'boards', page: p });
  if (live.standings.length) for (let p = 0; p < pages(live.standings.length); p++) list.push({ kind: 'standings', page: p });
  return list;
};

const slice = <T,>(rows: T[], page: number) => rows.slice(page * ROWS_PER_PAGE, (page + 1) * ROWS_PER_PAGE);

/** Pide al navegador no apagar la pantalla mientras esté visible (si el navegador no lo soporta, no pasa nada). */
const useWakeLock = () => {
  useEffect(() => {
    let lock: { release: () => Promise<void> } | undefined;
    const nav = navigator as Navigator & { wakeLock?: { request: (t: 'screen') => Promise<{ release: () => Promise<void> }> } };
    nav.wakeLock?.request('screen').then((l) => { lock = l; }).catch(() => undefined);
    return () => { void lock?.release(); };
  }, []);
};

const BoardsPanel = ({ live, page }: { live: LiveView; page: number }) => {
  const round = live.rounds.at(-1)!;
  return (
    <table className="cq-screen-table">
      <caption>Ronda {round.number} · emparejamientos</caption>
      <thead><tr><th scope="col">Mesa</th><th scope="col">Blancas</th><th scope="col">Resultado</th><th scope="col">Negras</th></tr></thead>
      <tbody>
        {slice(round.boards, page).map((b) => (
          <tr key={b.board}>
            <td>{b.board}</td><td>{b.white.name}</td>
            <td>{b.resultLabel ?? '—'}</td>
            <td>{b.black?.name ?? 'descansa'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
};

const StandingsPanel = ({ live, page }: { live: LiveView; page: number }) => (
  <table className="cq-screen-table">
    <caption>Clasificación</caption>
    <thead><tr><th scope="col">#</th><th scope="col">Jugador</th><th scope="col">Pts</th><th scope="col">Bu-1</th></tr></thead>
    <tbody>
      {slice(live.standings, page).map((r) => (
        <tr key={r.player.playerId}><td>{r.position}</td><td>{r.player.name}</td><td>{r.points.toFixed(1)}</td><td>{r.buchholzCut1.toFixed(1)}</td></tr>
      ))}
    </tbody>
  </table>
);

/** Rotación de paneles cada 15 s, con pausa (WCAG 2.2.2: el contenido que se mueve solo se puede detener). */
const useRotation = (count: number) => {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  useEffect(() => {
    if (paused || count < 2) return undefined;
    const timer = setInterval(() => setIndex((i) => i + 1), ROTATE_MS);
    return () => clearInterval(timer);
  }, [paused, count]);
  return { index: count ? index % count : 0, paused, toggle: () => setPaused(!paused) };
};

const ScreenHeader = ({ live, id }: { live: LiveView; id: number }) => {
  const t = live.detail.tournament;
  const state = t.status === 'OPEN' ? `Inscritos: ${t.playerCount}` : `Ronda ${t.currentRound} de ${t.roundsPlanned}`;
  return (
    <header className="cq-screen-head">
      <div>
        <h1>{t.name}</h1>
        <p>{state}{t.status === 'FINISHED' ? ' · torneo terminado' : ''}</p>
      </div>
      <div className="cq-screen-qr">
        <QrCode value={`${window.location.origin}/torneos/${id}`} label="QR para seguir el torneo desde el celular" size={120} />
        <span>Síguelo en tu celular</span>
      </div>
    </header>
  );
};

const PanelView = ({ live, panel }: { live: LiveView; panel: Panel | undefined }) => {
  if (!panel) return <p className="cq-screen-empty">El torneo aún no comienza. {live.detail.tournament.playerCount} inscritos.</p>;
  return panel.kind === 'boards' ? <BoardsPanel live={live} page={panel.page} /> : <StandingsPanel live={live} page={panel.page} />;
};

/**
 * /torneos/:id/pantalla: para el monitor o la TV de la sala. Rota entre los emparejamientos de la ronda en curso y la
 * clasificación (letra grande, alto contraste), se actualiza sola con cada resultado y no deja que la pantalla se
 * apague. La rotación se puede pausar. Un QR invita a seguir el torneo desde el celular.
 */
export const TournamentScreen = () => {
  const id = Number(useParams().id);
  const live = useLiveTournament(id);
  useWakeLock();
  const panels = live.data ? panelsOf(live.data) : [];
  const rotation = useRotation(panels.length);
  if (live.isLoading) return <Skeleton height={400} />;
  if (!live.data) return <ErrorAlert message="No encontramos ese torneo" />;
  return (
    <main className="cq-screen">
      <ScreenHeader live={live.data} id={id} />
      <section aria-live="polite"><PanelView live={live.data} panel={panels[rotation.index]} /></section>
      {panels.length > 1 && (
        <Button size="sm" variant="secondary" onClick={rotation.toggle}>{rotation.paused ? 'Reanudar rotación' : 'Pausar rotación'}</Button>
      )}
    </main>
  );
};
