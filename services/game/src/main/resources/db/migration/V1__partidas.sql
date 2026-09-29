-- Partidas en línea. Las jugadas se guardan en UCI (fuente de verdad) y en SAN (para mostrar); el tablero se
-- reconstruye reproduciendo las jugadas. Los relojes guardan el tiempo restante al inicio del turno en curso.

CREATE TABLE game (
    id                   BIGSERIAL    PRIMARY KEY,
    white_player_id      BIGINT       NOT NULL,
    black_player_id      BIGINT       NOT NULL,
    challenger_id        BIGINT       NOT NULL,
    white_name           VARCHAR(160) NOT NULL,       -- nombre público (menores abreviados)
    black_name           VARCHAR(160) NOT NULL,
    status               VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'FINISHED', 'DECLINED', 'CANCELLED', 'EXPIRED')),
    initial_seconds      INT          NOT NULL CHECK (initial_seconds BETWEEN 30 AND 10800),
    increment_seconds    INT          NOT NULL CHECK (increment_seconds BETWEEN 0 AND 180),
    rated                BOOLEAN      NOT NULL DEFAULT TRUE,
    moves_uci            TEXT         NOT NULL DEFAULT '',
    moves_san            TEXT         NOT NULL DEFAULT '',
    fen                  VARCHAR(100) NOT NULL,
    white_ms             BIGINT       NOT NULL,
    black_ms             BIGINT       NOT NULL,
    turn_started_at      TIMESTAMPTZ,
    draw_offer_by        BIGINT,
    result               VARCHAR(12)  CHECK (result IN ('WHITE_WINS', 'BLACK_WINS', 'DRAW')),
    termination          VARCHAR(30),
    white_rating_before  INT,
    black_rating_before  INT,
    white_unrated        BOOLEAN      NOT NULL DEFAULT FALSE,   -- sin rating de plataforma al empezar: K = 40
    black_unrated        BOOLEAN      NOT NULL DEFAULT FALSE,
    white_rating_after   INT,
    black_rating_after   INT,
    pgn                  TEXT,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at           TIMESTAMPTZ,
    finished_at          TIMESTAMPTZ
);
CREATE INDEX idx_game_white ON game (white_player_id, status);
CREATE INDEX idx_game_black ON game (black_player_id, status);
CREATE INDEX idx_game_status ON game (status);

CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
