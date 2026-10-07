-- tournament — V3: inscripción completa y acreditación.
--  * Reglas de inscripción del torneo: cupo, fecha de cierre, aprobación del organizador, rango de rating y si exige
--    acreditarse el día del torneo (quien no se acredita no juega la ronda 1).
--  * Estado de cada inscripción: PENDING (espera aprobación), CONFIRMED, WAITLIST (cupo lleno) o WITHDRAWN (retirado;
--    withdrawn_from_round = primera ronda que ya no juega; 1 = no se presentó).
--  * checkin_code: token aleatorio para el QR de acreditación (sin datos personales).

ALTER TABLE tournament
    ADD COLUMN registration_closes_at TIMESTAMPTZ,
    ADD COLUMN max_players            INT CHECK (max_players BETWEEN 2 AND 500),
    ADD COLUMN requires_approval      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN min_rating             INT CHECK (min_rating BETWEEN 0 AND 3500),
    ADD COLUMN max_rating             INT CHECK (max_rating BETWEEN 0 AND 3500),
    ADD COLUMN checkin_required       BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE registration
    ADD COLUMN status               VARCHAR(12) NOT NULL DEFAULT 'CONFIRMED'
                                    CHECK (status IN ('PENDING', 'CONFIRMED', 'WAITLIST', 'WITHDRAWN')),
    ADD COLUMN checkin_code         VARCHAR(32),
    ADD COLUMN checked_in_at        TIMESTAMPTZ,
    ADD COLUMN withdrawn_from_round INT;

-- Las inscripciones que ya existían quedan confirmadas, con su código de acreditación (122 bits aleatorios)
UPDATE registration SET checkin_code = replace(gen_random_uuid()::text, '-', '') WHERE checkin_code IS NULL;
ALTER TABLE registration ALTER COLUMN checkin_code SET NOT NULL;
CREATE UNIQUE INDEX ux_registration_checkin_code ON registration (checkin_code);
