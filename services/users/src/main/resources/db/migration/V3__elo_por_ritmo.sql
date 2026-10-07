-- =============================================================================
-- users — V3: ELO ChessQuery separado por ritmo (bala, relámpago, rápido, clásico).
--
-- Antes había un solo rating de plataforma (elo_platform / PLATFORM). Cada partida o torneo actualiza ahora el
-- ELO de su ritmo (TimeControlCategory en libs/common). Lo existente pasa a "rápido": las partidas jugadas hasta
-- hoy eran mayormente de 10 a 15 minutos y los torneos del club, rápidos.
-- =============================================================================

ALTER TABLE player
    ADD COLUMN elo_platform_bullet    INTEGER,
    ADD COLUMN elo_platform_blitz     INTEGER,
    ADD COLUMN elo_platform_rapid     INTEGER,
    ADD COLUMN elo_platform_classical INTEGER;

UPDATE player SET elo_platform_rapid = elo_platform WHERE elo_platform IS NOT NULL;

ALTER TABLE player DROP COLUMN elo_platform;

ALTER TABLE rating_history DROP CONSTRAINT rating_history_rating_type_check;
UPDATE rating_history SET rating_type = 'PLATFORM_RAPID' WHERE rating_type = 'PLATFORM';
ALTER TABLE rating_history ADD CONSTRAINT rating_history_rating_type_check CHECK (rating_type IN (
    'NATIONAL', 'FIDE_STANDARD', 'FIDE_RAPID', 'FIDE_BLITZ',
    'PLATFORM_BULLET', 'PLATFORM_BLITZ', 'PLATFORM_RAPID', 'PLATFORM_CLASSICAL',
    'LICHESS_BULLET', 'LICHESS_BLITZ', 'LICHESS_RAPID', 'LICHESS_CLASSICAL',
    'CHESSCOM_BULLET', 'CHESSCOM_BLITZ', 'CHESSCOM_RAPID', 'CHESSCOM_DAILY'));
