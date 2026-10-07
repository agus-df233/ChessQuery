-- users — V4: invitación para reclamar un perfil del roster.
-- El organizador genera un enlace (o QR) con un token aleatorio para un jugador de su roster que aún no tiene cuenta.
-- Quien lo abre con su cuenta une ese perfil al suyo (historial de torneos y ratings incluidos). El token vence a los
-- 30 días y se borra al usarse.
ALTER TABLE player
    ADD COLUMN claim_token            VARCHAR(32),
    ADD COLUMN claim_token_created_at TIMESTAMPTZ;
CREATE UNIQUE INDEX ux_player_claim_token ON player (claim_token) WHERE claim_token IS NOT NULL;
