package cl.chessquery.users.identity;

import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserta el jugador de un usuario nuevo en su <b>propia transacción</b>. Si dos requests del mismo usuario llegan a la
 * vez (la web pide varias cosas al entrar por primera vez), uno de los dos choca con el índice único del sujeto: con la
 * inserción aislada, ese choque no deja inutilizable la sesión de Hibernate del request, que puede releer la fila que
 * creó el otro (antes respondía 500).
 */
@Component
@RequiredArgsConstructor
class PlayerProvisioner {

    private final PlayerRepository players;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Player insert(Player fresh) {
        return players.saveAndFlush(fresh);
    }
}
