-- tournament — V2: ritmo de juego estructurado (minutos base + incremento en segundos).
-- Define qué ELO ChessQuery se actualiza al cerrar un torneo (uno por ritmo: bala, relámpago, rápido, clásico).
-- time_control queda como etiqueta libre; los torneos existentes con el formato "90+30" o "60" se migran solos.
-- Lo que no se puede leer, o queda fuera de rango, se deja sin ritmo estructurado (cuenta como rápido).

ALTER TABLE tournament
    ADD COLUMN base_minutes      INT CHECK (base_minutes BETWEEN 1 AND 300),
    ADD COLUMN increment_seconds INT CHECK (increment_seconds BETWEEN 0 AND 180);

UPDATE tournament t
SET    base_minutes = parsed.base, increment_seconds = parsed.inc
FROM  (SELECT id,
              (m[1])::int                  AS base,
              coalesce((m[2])::int, 0)     AS inc
       FROM  (SELECT id, regexp_match(time_control, '^\s*(\d{1,3})\s*(?:\+\s*(\d{1,3}))?\s*$') AS m
              FROM tournament) src
       WHERE m IS NOT NULL) parsed
WHERE  t.id = parsed.id
  AND  parsed.base BETWEEN 1 AND 300
  AND  parsed.inc BETWEEN 0 AND 180;
