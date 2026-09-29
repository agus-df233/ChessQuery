package cl.chessquery.tournament.events;

import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.common.events.Payloads;
import cl.chessquery.tournament.domain.Registration;
import cl.chessquery.tournament.domain.Repositories;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Consume {@code player.merged}: un jugador reclamó una ficha federada, así que sus inscripciones y mesas pasan
 * de la ficha ({@code fromPlayerId}) a su cuenta ({@code intoPlayerId}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayerMergedConsumer {

    private final IdempotentConsumer idempotent;
    private final Repositories.Registrations registrations;
    private final Repositories.Pairings pairings;

    @SqsListener("${chessquery.events.queues.players}")
    public void onMerged(ChessEvent event) {
        if (!TournamentEvents.PLAYER_MERGED.equals(event.eventType())) return;
        idempotent.handle(event, this::apply);
    }

    @org.springframework.transaction.annotation.Transactional
    public void apply(ChessEvent event) {
        Long from = Payloads.lng(event.payload(), "fromPlayerId");
        Long into = Payloads.lng(event.payload(), "intoPlayerId");
        if (from == null || into == null) return;
        for (Registration r : registrations.findByPlayerId(from)) {
            boolean alreadyIn = registrations.findByTournamentIdAndPlayerId(r.getTournamentId(), into).isPresent();
            if (!alreadyIn) {
                r.setPlayerId(into);
                registrations.save(r);
            }
        }
        int boards = pairings.reassignWhite(from, into) + pairings.reassignBlack(from, into);
        log.info("Jugador {} unido a {}: {} mesas reasignadas", from, into, boards);
    }
}
