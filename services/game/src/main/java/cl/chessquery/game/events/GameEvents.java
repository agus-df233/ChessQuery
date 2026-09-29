package cl.chessquery.game.events;

/** Routing keys que produce game (contratos en docs/events.md). */
public final class GameEvents {

    public static final String GAME_FINISHED = "game.finished";
    public static final String ELO_UPDATED = "elo.updated";

    private GameEvents() {}
}
