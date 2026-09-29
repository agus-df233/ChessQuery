-- Torneos presenciales del club. Los jugadores se referencian por playerId (dueño: servicio users);
-- se guarda una foto del nombre público y del rating al inscribirse para que la tabla y el TRF no dependan de users.

CREATE TABLE tournament (
    id               BIGSERIAL    PRIMARY KEY,
    organization_id  BIGINT       NOT NULL,
    organizer_id     BIGINT       NOT NULL,             -- playerId del dueño del club
    name             VARCHAR(160) NOT NULL,
    city             VARCHAR(100),
    region           VARCHAR(100),
    start_date       DATE         NOT NULL,
    end_date         DATE,
    format           VARCHAR(20)  NOT NULL CHECK (format IN ('SWISS', 'ROUND_ROBIN')),
    rounds_planned   INT          NOT NULL CHECK (rounds_planned BETWEEN 1 AND 30),
    time_control     VARCHAR(40),
    rated            BOOLEAN      NOT NULL DEFAULT TRUE,  -- al cerrar actualiza el rating de plataforma
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'FINISHED')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ
);
CREATE INDEX idx_tournament_org ON tournament (organization_id);
CREATE INDEX idx_tournament_status ON tournament (status, start_date);

CREATE TABLE registration (
    id              BIGSERIAL    PRIMARY KEY,
    tournament_id   BIGINT       NOT NULL REFERENCES tournament (id) ON DELETE CASCADE,
    player_id       BIGINT       NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,              -- nombre público (menores abreviados)
    title           VARCHAR(5),
    club_name       VARCHAR(160),
    seed_rating     INT          NOT NULL,              -- plataforma → nacional → FIDE → 1500
    platform_rating INT,                                -- rating de plataforma al inscribirse (null = sin rating)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tournament_id, player_id)
);
CREATE INDEX idx_registration_player ON registration (player_id);

CREATE TABLE round (
    id             BIGSERIAL   PRIMARY KEY,
    tournament_id  BIGINT      NOT NULL REFERENCES tournament (id) ON DELETE CASCADE,
    number         INT         NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tournament_id, number)
);

CREATE TABLE pairing (
    id               BIGSERIAL   PRIMARY KEY,
    round_id         BIGINT      NOT NULL REFERENCES round (id) ON DELETE CASCADE,
    board            INT         NOT NULL,
    white_player_id  BIGINT      NOT NULL,
    black_player_id  BIGINT,                             -- null = bye
    result           VARCHAR(20) CHECK (result IN ('WHITE_WINS', 'BLACK_WINS', 'DRAW', 'WHITE_FORFEIT_WIN',
                                                   'BLACK_FORFEIT_WIN', 'DOUBLE_FORFEIT', 'BYE')),
    UNIQUE (round_id, board)
);

-- Calendario de la Federación (evento federation.tournament.published del ETL): solo datos del evento.
CREATE TABLE federation_tournament (
    federation_tournament_id VARCHAR(20)  PRIMARY KEY,
    title                    VARCHAR(200) NOT NULL,
    city                     VARCHAR(100),
    region                   VARCHAR(100),
    club_name                VARCHAR(160),
    start_date               DATE         NOT NULL,
    end_date                 DATE,
    type                     VARCHAR(60),
    rounds                   INT,
    time_control             VARCHAR(60),
    category                 VARCHAR(60),
    rated_national           BOOLEAN      NOT NULL DEFAULT FALSE,
    rated_fide               BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_federation_tournament_start ON federation_tournament (start_date);

CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
