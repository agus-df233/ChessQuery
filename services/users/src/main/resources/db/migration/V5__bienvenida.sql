-- users — V5: asistente de bienvenida.
-- Un jugador nuevo elige su ritmo favorito (el desafío lo propone por defecto) y se marca cuándo terminó u omitió la
-- bienvenida. Los jugadores que ya existían quedan marcados: el asistente es solo para quien recién llega.
ALTER TABLE player
    ADD COLUMN preferred_category VARCHAR(16)
        CHECK (preferred_category IN ('BULLET', 'BLITZ', 'RAPID', 'CLASSICAL')),
    ADD COLUMN welcomed_at        TIMESTAMPTZ;
UPDATE player SET welcomed_at = now();
