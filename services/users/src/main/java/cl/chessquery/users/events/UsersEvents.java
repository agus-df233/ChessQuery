package cl.chessquery.users.events;

/** Routing keys que este servicio publica o consume. Catálogo completo en docs/events.md. */
public final class UsersEvents {

    // Publicados
    public static final String PLAYER_PROVISIONED = "player.provisioned";
    public static final String PLAYER_CLAIMED = "player.claimed";
    public static final String PLAYER_UPDATED = "player.updated";
    public static final String PLAYER_DELETED = "player.deleted";
    public static final String PROVISIONAL_CREATED = "player.provisional.created";
    public static final String FRIEND_REQUEST_CREATED = "friend.request.created";
    public static final String FRIEND_REQUEST_ACCEPTED = "friend.request.accepted";

    // Consumidos
    public static final String ELO_UPDATED = "elo.updated";
    public static final String RATING_UPDATED = "rating.updated";

    private UsersEvents() {}
}
