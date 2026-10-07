-- Desafíos abiertos: un jugador publica un enlace (o QR) y lo acepta el primero que entre, aunque no sean amigos.
-- La partida (game) se crea recién al aceptar, ya en juego: así game nunca tiene un rival vacío.
CREATE TABLE open_challenge (
    id                 BIGSERIAL    PRIMARY KEY,
    token              VARCHAR(32)  NOT NULL UNIQUE,      -- aleatorio (128 bits): el enlace no se puede adivinar
    challenger_id      BIGINT       NOT NULL,
    challenger_name    VARCHAR(160) NOT NULL,             -- nombre público (menores abreviados)
    minutes            INT          NOT NULL CHECK (minutes BETWEEN 1 AND 180),
    increment_seconds  INT          NOT NULL CHECK (increment_seconds BETWEEN 0 AND 180),
    color              VARCHAR(6)   NOT NULL CHECK (color IN ('WHITE', 'BLACK', 'RANDOM')),
    rated              BOOLEAN      NOT NULL,
    status             VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'ACCEPTED', 'CANCELLED')),
    game_id            BIGINT,
    created_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0    -- dos que aceptan a la vez: gana uno solo
);
CREATE INDEX idx_open_challenge_challenger ON open_challenge (challenger_id, status);
