import { useState } from 'react';
import { Button, Card } from '@chessquery/ui-lib';
import type { EntryView, RoundView, StandingView } from '../../api/tournamentTypes';
import { follow, followed, historyOf, standingOf } from '../../lib/follow';

/**
 * Para el apoderado (o cualquiera en la sala): elegir a un jugador y ver, en el celular y en vivo, su mesa y rival de
 * la ronda en curso, su posición y su historia ronda a ronda. Sin cuenta; solo nombres públicos (menores abreviados).
 */
export const FollowPlayer = ({ tournamentId, players, rounds, standings }: {
  tournamentId: number; players: EntryView[]; rounds: RoundView[]; standings: StandingView[];
}) => {
  const [playerId, setPlayerId] = useState<number | null>(() => followed(tournamentId));
  const [query, setQuery] = useState('');
  const choose = (id: number | null) => { follow(tournamentId, id); setPlayerId(id); setQuery(''); };
  const me = players.find((p) => p.player.playerId === playerId);
  const matches = query.trim().length < 2 ? [] : players
    .filter((p) => p.player.name.toLowerCase().includes(query.trim().toLowerCase())).slice(0, 8);

  if (!me) {
    return (
      <Card header="Seguir a un jugador">
        <label>Busca a tu hijo, a un amigo o a cualquier jugador
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Nombre" />
        </label>
        <ul className="cq-list">
          {matches.map((p) => (
            <li key={p.player.playerId}><Button size="sm" variant="secondary" onClick={() => choose(p.player.playerId)}>Seguir a {p.player.name}</Button></li>
          ))}
        </ul>
      </Card>
    );
  }
  const row = standingOf(standings, me.player.playerId);
  const history = historyOf(rounds, me.player.playerId);
  const current = history[0];
  return (
    <Card header={`Siguiendo a ${me.player.name}`}>
      <div role="status" aria-live="polite">
        {current && (
          <p style={{ fontSize: 18, margin: '0 0 8px' }}>
            <strong>Ronda {current.round}:</strong>{' '}
            {current.board && current.rival ? `mesa ${current.board}, con ${current.color} contra ${current.rival} · ${current.outcome}` : current.outcome}
          </p>
        )}
        {!current && <p>Las rondas todavía no empiezan.</p>}
        {row && <p className="cq-muted">Va {row.position}° con {row.points.toFixed(1)} puntos.</p>}
      </div>
      {history.length > 1 && (
        <ul className="cq-list" aria-label={`Rondas de ${me.player.name}`}>
          {history.slice(1).map((h) => (
            <li key={h.round}>Ronda {h.round}: {h.rival ? `${h.outcome} con ${h.color} contra ${h.rival}` : h.outcome}</li>
          ))}
        </ul>
      )}
      <Button size="sm" variant="secondary" onClick={() => choose(null)}>Dejar de seguir</Button>
    </Card>
  );
};
