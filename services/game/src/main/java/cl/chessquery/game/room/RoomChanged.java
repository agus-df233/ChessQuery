package cl.chessquery.game.room;

/** Una sala cambió (miembros, tableros o una jugada en alguna de sus partidas); se publica tras el commit. */
public record RoomChanged(long roomId) {}
