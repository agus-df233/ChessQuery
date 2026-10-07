package cl.chessquery.tournament.domain;

/**
 * PENDING: espera que el organizador la apruebe. CONFIRMED: juega. WAITLIST: el cupo estaba lleno; entra sola si se
 * libera uno. WITHDRAWN: retirado (desde {@code Registration.withdrawnFromRound}; 1 = no se presentó).
 */
public enum RegistrationStatus { PENDING, CONFIRMED, WAITLIST, WITHDRAWN }
