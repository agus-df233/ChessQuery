package cl.chessquery.users.rating;

import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.IdempotentConsumer;
import cl.chessquery.users.catalog.ClubRepository;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Ramas que la integración no toca: payloads incompletos, tipos desconocidos, eventos ajenos. */
class ConsumersUnitTest {

    private final IdempotentConsumer idempotent = mock(IdempotentConsumer.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final ClubRepository clubs = mock(ClubRepository.class);
    private final RatingService ratings = mock(RatingService.class);

    @Test
    void eloConsumerIgnoresForeignIncompleteAndUnknownEvents() {
        EloUpdatedConsumer c = new EloUpdatedConsumer(idempotent, players, ratings);
        c.onEloUpdated(ChessEvent.of("other.event", Map.of()));
        verify(idempotent, never()).handle(any(), any());

        c.onEloUpdated(ChessEvent.of(UsersEvents.ELO_UPDATED, Map.of()));
        verify(idempotent).handle(any(), any());

        c.apply(ChessEvent.of(UsersEvents.ELO_UPDATED, Map.of("playerId", 1)));
        c.apply(ChessEvent.of(UsersEvents.ELO_UPDATED, Map.of("playerId", 1, "newElo", 10, "ratingType", "NOPE")));
        when(players.findById(7L)).thenReturn(Optional.empty());
        c.apply(ChessEvent.of(UsersEvents.ELO_UPDATED, Map.of("playerId", "7", "newElo", "10", "ratingType", "PLATFORM")));
        verifyNoInteractions(ratings);
    }

    @Test
    void ratingConsumerHandlesMissingSourceUnknownUsernameAndCreateRace() {
        RatingUpdatedConsumer c = new RatingUpdatedConsumer(idempotent, players, clubs, ratings);
        c.onRatingUpdated(ChessEvent.of("x", Map.of()));
        c.onRatingUpdated(ChessEvent.of(UsersEvents.RATING_UPDATED, Map.of()));
        verify(idempotent, times(1)).handle(any(), any());

        c.apply(ChessEvent.of(UsersEvents.RATING_UPDATED, Map.of("players", List.of())));      // sin fuente
        c.apply(ChessEvent.of(UsersEvents.RATING_UPDATED, Map.of("source", "CHESSCOM", "players", List.of(
                Map.of("chesscomUsername", "nadie"), "basura"))));
        when(players.findByChesscomUsernameIgnoreCase("nadie")).thenReturn(Optional.empty());

        // AJEFECH: carrera al crear → se ignora sin romper el lote
        when(players.findByFullNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(players.saveAndFlush(any(Player.class))).thenThrow(new DataIntegrityViolationException("dup"));
        c.apply(ChessEvent.of(UsersEvents.RATING_UPDATED, Map.of("source", "AJEFECH", "players", List.of(
                Map.of("firstName", "A", "lastName", "B", "eloNational", 1200)))));
        verify(ratings, never()).apply(any(), any(), any(), any(), any());
    }

    @Test
    void ratingServiceSkipsNoOps() {
        RatingHistoryRepository history = mock(RatingHistoryRepository.class);
        RatingService service = new RatingService(players, history);
        Player p = Player.builder().firstName("a").lastName("b").eloNational(1500).build();
        org.assertj.core.api.Assertions.assertThat(service.apply(p, RatingType.NATIONAL, null, null, "X")).isFalse();
        org.assertj.core.api.Assertions.assertThat(service.apply(p, RatingType.NATIONAL, 0, null, "X")).isFalse();
        org.assertj.core.api.Assertions.assertThat(service.apply(p, RatingType.NATIONAL, 1500, null, "X")).isFalse();
        org.assertj.core.api.Assertions.assertThat(service.apply(p, RatingType.FIDE_BLITZ, 1600, null, "X")).isTrue();
        verify(history).save(any(RatingHistory.class));
        org.assertj.core.api.Assertions.assertThat(p.rating(RatingType.FIDE_BLITZ)).isEqualTo(1600);
    }
}
