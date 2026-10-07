package cl.chessquery.game.room;

import cl.chessquery.game.domain.GameRepository;
import cl.chessquery.game.live.GameChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Una jugada (o el fin) en un tablero de una sala cambia la sala: la cuadrícula del organizador y de los espectadores
 * se actualiza sola. Escucha el mismo {@link GameChanged} que el WebSocket de la partida, fuera del hilo de la jugada.
 */
@Component
@RequiredArgsConstructor
public class RoomLive {

    private final GameRepository games;
    private final RoomService rooms;

    @Async("liveExecutor")
    @EventListener
    public void onGameChanged(GameChanged event) {
        games.findById(event.gameId())
                .filter(g -> g.getRoomId() != null)
                .ifPresent(g -> rooms.gameChanged(g.getRoomId()));
    }
}
