-- Conexiones WebSocket abiertas (API Gateway en la nube, /ws en local) y la partida que sigue cada una.
-- En la base y no en memoria: cualquier instancia de game puede avisar a todas las conexiones de una partida.
CREATE TABLE ws_connection (
    connection_id  VARCHAR(128) PRIMARY KEY,
    player_id      BIGINT       NOT NULL,
    game_id        BIGINT,
    connected_at   TIMESTAMPTZ  NOT NULL,
    last_seen_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_ws_connection_game ON ws_connection (game_id);
