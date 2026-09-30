package cl.chessquery.game.live;

/** Evento interno (Spring, no del bus): la partida cambió y ya se confirmó en la base de datos. */
public record GameChanged(long gameId) {}
