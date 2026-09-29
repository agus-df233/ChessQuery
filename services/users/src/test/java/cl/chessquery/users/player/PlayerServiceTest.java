package cl.chessquery.users.player;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.catalog.ClubRepository;
import cl.chessquery.users.catalog.CountryRepository;
import cl.chessquery.users.player.PlayerDtos.UpdateProfileRequest;
import cl.chessquery.users.rating.ExternalRatingsClient;
import cl.chessquery.users.rating.RatingService;
import cl.chessquery.users.rating.RatingType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlayerServiceTest {

    private final PlayerRepository players = mock(PlayerRepository.class);
    private final PlayerTitleRepository titles = mock(PlayerTitleRepository.class);
    private final ClubRepository clubs = mock(ClubRepository.class);
    private final CountryRepository countries = mock(CountryRepository.class);
    private final RatingService ratings = mock(RatingService.class);
    private final ExternalRatingsClient external = mock(ExternalRatingsClient.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final PlayerService service = new PlayerService(players, titles, clubs, countries, ratings, external, events,
            new cl.chessquery.users.privacy.IdentifierHasher("test-pepper-0123456789"));

    private static Player player(long id) {
        Player p = Player.builder().firstName("Ana").lastName("Soto").build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void updateProfileRejectsTakenRutAndUnknownCatalogIds() {
        Player me = player(1);
        when(players.findById(1L)).thenReturn(Optional.of(me));
        when(players.findByRutHash(new cl.chessquery.users.privacy.IdentifierHasher("test-pepper-0123456789").rut("1-9")))
                .thenReturn(Optional.of(player(2)));
        assertThatThrownBy(() -> service.updateProfile(1L, req(r -> r.rut = "1-9")))
                .isInstanceOf(ApiException.class).hasMessageContaining("RUT");

        when(countries.findById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProfile(1L, req(r -> r.countryId = 99))).hasMessageContaining("País");
        when(clubs.findById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProfile(1L, req(r -> r.clubId = 99))).hasMessageContaining("Club");

        when(players.findByChesscomUsernameIgnoreCase("x")).thenReturn(Optional.of(player(3)));
        assertThatThrownBy(() -> service.updateProfile(1L, req(r -> r.chesscom = "x"))).hasMessageContaining("Chess.com");
    }

    @Test
    void updateProfileWithNothingToChangePublishesNothing() {
        when(players.findById(1L)).thenReturn(Optional.of(player(1)));
        service.updateProfile(1L, req(r -> {}));
        verifyNoInteractions(events);

        // vaciar campos opcionales y quitar el propio RUT: permitido
        Player me = player(1);
        when(players.findById(1L)).thenReturn(Optional.of(me));
        when(players.findByRut("1-9")).thenReturn(Optional.of(me));
        service.updateProfile(1L, req(r -> { r.rut = "1-9"; r.displayName = " "; r.gender = ""; r.chesscom = " "; }));
        assertThat(me.getRut()).isEqualTo("1-9");
        assertThat(me.getDisplayName()).isNull();
        verify(events).publish(eq("player.updated"), any());
    }

    @Test
    void syncExternalRatingsAppliesEachSourceAndMarksEnrichment() {
        Player me = player(1);
        me.setLichessUsername("li");
        me.setChesscomUsername("cc");
        when(players.findById(1L)).thenReturn(Optional.of(me));
        when(external.lichess("li")).thenReturn(Optional.of(Map.of(RatingType.LICHESS_BLITZ, 1500)));
        when(external.chesscom("cc")).thenReturn(Optional.empty());

        service.syncExternalRatings(1L);
        verify(ratings).apply(eq(me), eq(RatingType.LICHESS_BLITZ), eq(1500), any(), eq("LICHESS"));
        verify(ratings).markEnriched(me, "LICHESS");
        verify(ratings, never()).markEnriched(me, "CHESSCOM"); // Chess.com no respondió: no se marca
    }

    @Test
    void summariesAndSearchGuardInputs() {
        assertThat(service.summaries(null)).isEmpty();
        assertThat(service.summaries(List.of())).isEmpty();
        assertThatThrownBy(() -> service.search(null, 5)).isInstanceOf(ApiException.class);
        when(players.searchFuzzy(eq("ana"), any(), eq(50))).thenReturn(List.of(player(1)));
        when(titles.currentTitlesOf(List.of(1L))).thenReturn(Map.of(1L, "FM"));
        assertThat(service.search("ana", 500).get(0).currentTitle()).isEqualTo("FM");
        assertThatThrownBy(() -> service.profileByEmail(" ")).hasMessageContaining("email");
        assertThatThrownBy(() -> service.profile(42L)).hasMessageContaining("42");
    }

    // Constructor de requests legible: solo los campos que cada test quiere tocar.
    private static class R { String rut, displayName, gender, chesscom; Integer countryId, clubId; }

    private static UpdateProfileRequest req(java.util.function.Consumer<R> fill) {
        R r = new R();
        fill.accept(r);
        return new UpdateProfileRequest(null, null, r.displayName, r.rut, null, r.gender, r.countryId, r.clubId,
                null, null, r.chesscom);
    }
}
