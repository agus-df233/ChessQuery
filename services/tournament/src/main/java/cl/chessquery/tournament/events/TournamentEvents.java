package cl.chessquery.tournament.events;

/** Routing keys que produce y consume tournament (contratos en docs/events.md). */
public final class TournamentEvents {

    public static final String ROUND_GENERATED = "tournament.round.generated";
    public static final String FINISHED = "tournament.finished";
    public static final String ELO_UPDATED = "elo.updated";

    public static final String FEDERATION_TOURNAMENT_PUBLISHED = "federation.tournament.published";
    public static final String PLAYER_MERGED = "player.merged";

    private TournamentEvents() {}
}
