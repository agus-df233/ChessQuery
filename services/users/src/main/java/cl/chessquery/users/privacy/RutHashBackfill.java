package cl.chessquery.users.privacy;

import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Completa {@code rut_hash} de las filas con RUT anteriores a la V2: SQL no puede calcular el HMAC
 * porque el pepper no vive en la base. Idempotente: solo toca filas con RUT y sin hash.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RutHashBackfill implements ApplicationRunner {

    private final PlayerRepository players;
    private final IdentifierHasher hasher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Player> pending = players.findByRutIsNotNullAndRutHashIsNull();
        pending.forEach(p -> p.setRutHash(hasher.rut(p.getRut())));
        players.saveAll(pending);
        if (!pending.isEmpty()) log.info("rut_hash completado en {} jugadores", pending.size());
    }
}
