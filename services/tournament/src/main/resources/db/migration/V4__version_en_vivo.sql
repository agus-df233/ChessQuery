-- tournament — V4: versión del torneo para el tiempo real (pantalla de la sala, apoderados).
-- Sube con cada cambio (inscripción, acreditación, ronda, resultado, cierre); el long polling público responde apenas
-- la versión pasa de la que tiene el cliente.
ALTER TABLE tournament ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
