-- Schema `users` (Flyway lo crea). Sin FKs hacia otros schemas: cada servicio es dueño de sus tablas.

CREATE TABLE player (
    id               BIGSERIAL PRIMARY KEY,
    external_subject VARCHAR(255),                 -- `sub` del IdP; NULL para jugadores provisorios
    email            VARCHAR(255),
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    display_name     VARCHAR(200),
    provisional      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_player_external_subject ON player (external_subject) WHERE external_subject IS NOT NULL;
CREATE INDEX ix_player_email ON player (lower(email));

CREATE TABLE organization (
    id              BIGSERIAL PRIMARY KEY,
    owner_player_id BIGINT       NOT NULL UNIQUE REFERENCES player (id),
    name            VARCHAR(150) NOT NULL,
    plan            VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_organization_plan CHECK (plan IN ('FREE', 'PRO'))
);

CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
