-- Salas de juego: el organizador (un colegio, un club) despliega N tableros para una clase o una práctica. Los
-- jugadores entran con un código, el organizador los asigna a los tableros y los que sobran miran como espectadores.
-- Cada tablero en juego es una partida normal (game.room_id), siempre sin rating.

CREATE TABLE room (
    id                 BIGSERIAL    PRIMARY KEY,
    organization_id    BIGINT       NOT NULL,
    organizer_id       BIGINT       NOT NULL,             -- playerId del organizador dueño
    name               VARCHAR(120) NOT NULL,
    code               VARCHAR(6)   NOT NULL,             -- para entrar; solo se reserva mientras la sala está abierta
    boards             INT          NOT NULL CHECK (boards BETWEEN 1 AND 16),
    max_players        INT          NOT NULL CHECK (max_players BETWEEN 2 AND 64),
    initial_seconds    INT          NOT NULL CHECK (initial_seconds BETWEEN 60 AND 10800),
    increment_seconds  INT          NOT NULL CHECK (increment_seconds BETWEEN 0 AND 180),
    status             VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    version            BIGINT       NOT NULL DEFAULT 0,   -- sube con cada cambio de la sala o de sus partidas
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_room_open_code ON room (code) WHERE status = 'OPEN';
CREATE INDEX idx_room_organization ON room (organization_id, status);

CREATE TABLE room_member (
    room_id      BIGINT       NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    player_id    BIGINT       NOT NULL,
    public_name  VARCHAR(160) NOT NULL,                   -- nombre público (menores abreviados)
    joined_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (room_id, player_id)
);
CREATE INDEX idx_room_member_player ON room_member (player_id);

-- Puestos de cada tablero. game_id = última partida del tablero (en juego o terminada).
CREATE TABLE room_board (
    room_id          BIGINT  NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    board_no         INT     NOT NULL CHECK (board_no BETWEEN 1 AND 16),
    white_player_id  BIGINT,
    black_player_id  BIGINT,
    game_id          BIGINT,
    PRIMARY KEY (room_id, board_no),
    CHECK (white_player_id IS NULL OR black_player_id IS NULL OR white_player_id <> black_player_id)
);

ALTER TABLE game
    ADD COLUMN room_id  BIGINT,
    ADD COLUMN board_no INT;
CREATE INDEX idx_game_room ON game (room_id) WHERE room_id IS NOT NULL;

-- Una conexión en vivo puede seguir una partida o una sala completa (la cuadrícula de tableros).
ALTER TABLE ws_connection ADD COLUMN room_id BIGINT;
CREATE INDEX idx_ws_connection_room ON ws_connection (room_id);
