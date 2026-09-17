-- =============================================================================
-- users — V1: esquema base del servicio (Flyway crea el schema `users`).
--
-- Reglas: cada servicio es dueño de sus tablas; no hay FKs hacia otros schemas.
-- Los ids de jugador (player.id) son la clave interna que usan tournament,
-- game y notifications; el `sub` del IdP vive solo acá (external_subject).
-- =============================================================================

-- Búsqueda difusa por nombre (similaridad de trigramas). Se instala en `public` a propósito:
-- Flyway corre con search_path = users y, si la extensión cayera ahí, el operador `%` no se
-- resolvería desde las sesiones normales (cuyo search_path es public).
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- ── Catálogo ────────────────────────────────────────────────────────────────
CREATE TABLE country (
    id              SERIAL       PRIMARY KEY,
    iso_code        VARCHAR(3)   NOT NULL UNIQUE,   -- ISO 3166-1 alpha-3
    name            VARCHAR(100) NOT NULL,
    fide_federation VARCHAR(10)
);

INSERT INTO country (iso_code, name, fide_federation) VALUES
    ('CHL', 'Chile', 'CHI'), ('ARG', 'Argentina', 'ARG'), ('PER', 'Perú', 'PER'),
    ('BOL', 'Bolivia', 'BOL'), ('BRA', 'Brasil', 'BRA'), ('COL', 'Colombia', 'COL'),
    ('ECU', 'Ecuador', 'ECU'), ('URY', 'Uruguay', 'URU'), ('PRY', 'Paraguay', 'PAR'),
    ('MEX', 'México', 'MEX'), ('USA', 'Estados Unidos', 'USA'), ('ESP', 'España', 'ESP');

-- Club federativo (catálogo AJEFECH). Distinto de `organization`, que es el
-- tenant del organizador dentro de la plataforma.
CREATE TABLE club (
    id              SERIAL       PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    country_id      INTEGER      REFERENCES country (id),
    city            VARCHAR(100),
    federation_code VARCHAR(20)
);
CREATE INDEX ix_club_name_lower ON club (lower(name));

-- ── Jugador ─────────────────────────────────────────────────────────────────
CREATE TABLE player (
    id                       BIGSERIAL    PRIMARY KEY,
    external_subject         VARCHAR(255),              -- `sub` del IdP; NULL en provisorios y filas federadas
    first_name               VARCHAR(100) NOT NULL,
    last_name                VARCHAR(100) NOT NULL,
    display_name             VARCHAR(200),
    email                    VARCHAR(255),              -- siempre normalizado (trim + minúsculas)
    rut                      VARCHAR(12),               -- "12345678-9"; NULL para extranjeros
    birth_date               DATE,
    gender                   VARCHAR(1)   CHECK (gender IN ('M', 'F', 'O')),
    country_id               INTEGER      REFERENCES country (id),
    region                   VARCHAR(100),
    club_id                  INTEGER      REFERENCES club (id),
    -- Identificadores federativos (públicos por definición)
    fide_id                  VARCHAR(20),
    federation_id            VARCHAR(50),               -- id en AJEFECH
    -- Cuentas externas que el jugador vincula
    lichess_username         VARCHAR(100),
    chesscom_username        VARCHAR(100),
    -- Snapshots de rating (el historial completo está en rating_history)
    elo_national             INTEGER,
    elo_fide_standard        INTEGER,
    elo_fide_rapid           INTEGER,
    elo_fide_blitz           INTEGER,
    elo_platform             INTEGER,
    elo_lichess_bullet       INTEGER,
    elo_lichess_blitz        INTEGER,
    elo_lichess_rapid        INTEGER,
    elo_lichess_classical    INTEGER,
    elo_chesscom_bullet      INTEGER,
    elo_chesscom_blitz       INTEGER,
    elo_chesscom_rapid       INTEGER,
    elo_chesscom_daily       INTEGER,
    -- Trazabilidad del último enriquecimiento externo (AJEFECH, LICHESS, CHESSCOM)
    enrichment_source        VARCHAR(20),
    enriched_at              TIMESTAMPTZ,
    -- Roster provisorio: jugador cargado por un organizador, sin cuenta todavía
    provisional              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by_organizer_id  BIGINT,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    tags                     VARCHAR(300),              -- etiquetas separadas por coma
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Unicidad de identidad: parciales para permitir NULL sin colisiones.
CREATE UNIQUE INDEX ux_player_external_subject ON player (external_subject) WHERE external_subject IS NOT NULL;
CREATE UNIQUE INDEX ux_player_email            ON player (email)             WHERE email IS NOT NULL;
CREATE UNIQUE INDEX ux_player_rut              ON player (rut)               WHERE rut IS NOT NULL;
CREATE UNIQUE INDEX ux_player_fide_id          ON player (fide_id)           WHERE fide_id IS NOT NULL;
CREATE UNIQUE INDEX ux_player_federation_id    ON player (federation_id)     WHERE federation_id IS NOT NULL;
CREATE UNIQUE INDEX ux_player_lichess          ON player (lower(lichess_username))  WHERE lichess_username IS NOT NULL;
CREATE UNIQUE INDEX ux_player_chesscom         ON player (lower(chesscom_username)) WHERE chesscom_username IS NOT NULL;

-- Búsqueda difusa: el índice y la consulta usan exactamente la misma expresión.
CREATE INDEX ix_player_fullname_trgm ON player USING gin (lower(first_name || ' ' || last_name) gin_trgm_ops);
CREATE INDEX ix_player_fullname_lower ON player (lower(first_name || ' ' || last_name));
-- Ranking nacional: por región y categoría de edad, ordenado por ELO.
CREATE INDEX ix_player_ranking ON player (elo_national DESC NULLS LAST) WHERE elo_national IS NOT NULL;
CREATE INDEX ix_player_region_lower ON player (lower(region));
CREATE INDEX ix_player_birth_date ON player (birth_date);
-- Roster del organizador.
CREATE INDEX ix_player_roster ON player (created_by_organizer_id) WHERE provisional = TRUE;

-- ── Ratings ─────────────────────────────────────────────────────────────────
CREATE TABLE rating_history (
    id                BIGSERIAL   PRIMARY KEY,
    player_id         BIGINT      NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    rating_type       VARCHAR(30) NOT NULL CHECK (rating_type IN (
                          'NATIONAL', 'FIDE_STANDARD', 'FIDE_RAPID', 'FIDE_BLITZ', 'PLATFORM',
                          'LICHESS_BULLET', 'LICHESS_BLITZ', 'LICHESS_RAPID', 'LICHESS_CLASSICAL',
                          'CHESSCOM_BULLET', 'CHESSCOM_BLITZ', 'CHESSCOM_RAPID', 'CHESSCOM_DAILY')),
    rating_value      INTEGER     NOT NULL,
    rating_prev_value INTEGER,                 -- desnormalizado para graficar deltas sin self-join
    delta             SMALLINT,
    recorded_at       TIMESTAMPTZ NOT NULL,
    source            VARCHAR(50)              -- GAME, AJEFECH, LICHESS, CHESSCOM, MANUAL
);
CREATE INDEX ix_rating_history_player ON rating_history (player_id, rating_type, recorded_at DESC);

CREATE TABLE player_title_history (
    id         BIGSERIAL   PRIMARY KEY,
    player_id  BIGINT      NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    title      VARCHAR(10) NOT NULL CHECK (title IN ('GM','IM','FM','CM','WGM','WIM','WFM','WCM')),
    title_date DATE        NOT NULL,
    is_current BOOLEAN     NOT NULL DEFAULT TRUE,
    source     VARCHAR(50)
);
CREATE INDEX ix_title_player_current ON player_title_history (player_id) WHERE is_current = TRUE;

-- ── Organización (club del organizador como tenant) ─────────────────────────
-- Ser dueño de una organización ES tener el rol ORGANIZER. 1 jugador = 1 club.
CREATE TABLE organization (
    id              BIGSERIAL    PRIMARY KEY,
    owner_player_id BIGINT       NOT NULL UNIQUE REFERENCES player (id),
    name            VARCHAR(150) NOT NULL,
    city            VARCHAR(120),
    description     VARCHAR(500),
    logo_url        VARCHAR(500),
    plan            VARCHAR(20)  NOT NULL DEFAULT 'FREE' CHECK (plan IN ('FREE', 'PRO')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Amistades ───────────────────────────────────────────────────────────────
-- Una sola fila por par (índice funcional LEAST/GREATEST). Rechazar o dejar
-- de ser amigos borra la fila: no hay estados DECLINED/REMOVED.
CREATE TABLE friendship (
    id           BIGSERIAL   PRIMARY KEY,
    requester_id BIGINT      NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    addressee_id BIGINT      NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT ck_friendship_distinct CHECK (requester_id <> addressee_id)
);
CREATE UNIQUE INDEX ux_friendship_pair ON friendship (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX ix_friendship_requester ON friendship (requester_id, status);
CREATE INDEX ix_friendship_addressee ON friendship (addressee_id, status);

-- ── Idempotencia de consumers RabbitMQ ──────────────────────────────────────
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
