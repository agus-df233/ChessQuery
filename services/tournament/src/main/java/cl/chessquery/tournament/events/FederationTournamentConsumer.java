package cl.chessquery.tournament.events;

import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.common.events.Payloads;
import cl.chessquery.tournament.domain.FederationTournament;
import cl.chessquery.tournament.domain.Repositories;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Consume {@code federation.tournament.published} (ETL de la Federación): inserta o actualiza el calendario
 * federativo que se muestra junto a los torneos de los clubes. Filas sin id, título o fecha se ignoran.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FederationTournamentConsumer {

    private final IdempotentConsumer idempotent;
    private final Repositories.FederationTournaments calendar;

    @SqsListener("${chessquery.events.queues.federation}")
    public void onPublished(ChessEvent event) {
        if (!TournamentEvents.FEDERATION_TOURNAMENT_PUBLISHED.equals(event.eventType())) return;
        idempotent.handle(event, this::apply);
    }

    @SuppressWarnings("unchecked")
    public void apply(ChessEvent event) {
        Object rows = event.payload().get("tournaments");
        if (!(rows instanceof List<?> list)) return;
        int saved = 0;
        for (Object row : list) {
            if (row instanceof Map<?, ?> m && upsert((Map<String, Object>) m)) saved++;
        }
        log.info("Calendario de la Federación: {} torneos actualizados", saved);
    }

    private boolean upsert(Map<String, Object> m) {
        String id = Payloads.str(m, "federationTournamentId");
        String title = Payloads.str(m, "title");
        if (id == null || title == null || Payloads.date(m, "startDate") == null) return false;
        FederationTournament t = calendar.findById(id).orElseGet(FederationTournament::new);
        t.setFederationTournamentId(id);
        t.setTitle(title);
        t.setCity(Payloads.str(m, "city"));
        t.setRegion(Payloads.str(m, "region"));
        t.setClubName(Payloads.str(m, "clubName"));
        t.setStartDate(Payloads.date(m, "startDate"));
        t.setEndDate(Payloads.date(m, "endDate"));
        t.setType(Payloads.str(m, "type"));
        t.setRounds(Payloads.integer(m, "rounds"));
        t.setTimeControl(Payloads.str(m, "timeControl"));
        t.setCategory(Payloads.str(m, "category"));
        t.setRatedNational(Boolean.TRUE.equals(m.get("ratedNational")));
        t.setRatedFide(Boolean.TRUE.equals(m.get("ratedFide")));
        t.setUpdatedAt(Instant.now());
        calendar.save(t);
        return true;
    }
}
