-- =============================================================================
-- users — V2: minimización de datos y derechos del titular (Ley 19.628 / 21.719).
--
--  * birth_year: las categorías de ajedrez se calculan por año de nacimiento. De los
--    jugadores federados no reclamados solo se guarda el año, nunca la fecha completa.
--  * rut_hash: HMAC-SHA256 del RUT normalizado (pepper fuera de la BD). Permite hacer
--    match con fuentes externas sin guardar el RUT en claro de terceros. El RUT en
--    claro solo existe si lo ingresó su titular o el organizador que lo inscribió.
--  * data_suppression: identificadores (hasheados) de quien pidió supresión u oposición.
--    El ETL y el consumer de rating.updated los saltan: borrar tiene que ser definitivo.
-- =============================================================================

ALTER TABLE player
    ADD COLUMN birth_year          INTEGER,
    ADD COLUMN rut_hash            VARCHAR(64),
    ADD COLUMN source_url          VARCHAR(300),
    ADD COLUMN source_period       VARCHAR(7),      -- "YYYY-MM" de la lista de origen
    ADD COLUMN parental_consent_at TIMESTAMPTZ;     -- menores de 14 con cuenta

UPDATE player SET birth_year = EXTRACT(YEAR FROM birth_date) WHERE birth_date IS NOT NULL;

CREATE UNIQUE INDEX ux_player_rut_hash ON player (rut_hash) WHERE rut_hash IS NOT NULL;
CREATE INDEX ix_player_birth_year ON player (birth_year);
DROP INDEX IF EXISTS ix_player_birth_date;

CREATE TABLE data_suppression (
    identifier_hash VARCHAR(64) PRIMARY KEY,   -- HMAC de "fide:<id>", "fed:<id>" o "rut:<rut>"
    reason          VARCHAR(40) NOT NULL,      -- ERASURE (supresión) | OBJECTION (oposición)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
