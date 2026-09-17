package cl.chessquery.users.roster;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.catalog.ClubRepository;
import cl.chessquery.users.organization.OrganizationDtos.PlanInfo;
import cl.chessquery.users.organization.OrganizationService;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RosterServiceTest {

    private final PlayerRepository players = mock(PlayerRepository.class);
    private final ClubRepository clubs = mock(ClubRepository.class);
    private final OrganizationService organizations = mock(OrganizationService.class);
    private final RosterService service = new RosterService(players, clubs, organizations, mock(EventPublisher.class));

    private static RosterDtos.CreateRequest req(String email, Integer clubId) {
        return new RosterDtos.CreateRequest("Ana", "Soto", null, email, 0, null, clubId, List.of());
    }

    @Test
    void planLimitEmailAndClubValidations() {
        when(organizations.planOf(1L)).thenReturn(new PlanInfo(1L, "FREE", 1, 3));
        when(organizations.rosterCount(1L)).thenReturn(1);
        assertThatThrownBy(() -> service.add(1L, req(null, null))).hasMessageContaining("límite");

        when(organizations.rosterCount(1L)).thenReturn(0);
        when(players.findByEmail("a@x.cl")).thenReturn(Optional.of(Player.builder().firstName("x").lastName("y").build()));
        assertThatThrownBy(() -> service.add(1L, req("A@x.cl", null))).hasMessageContaining("email");

        when(clubs.findById(9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(1L, req(null, 9))).hasMessageContaining("Club");
    }

    @Test
    void onlyOwnerCanManageProvisionals() {
        Player mine = Player.builder().firstName("a").lastName("b").provisional(true).createdByOrganizerId(1L).build();
        ReflectionTestUtils.setField(mine, "id", 5L);
        Player notProvisional = Player.builder().firstName("a").lastName("b").build();
        when(players.findById(5L)).thenReturn(Optional.of(mine));
        when(players.findById(6L)).thenReturn(Optional.of(notProvisional));
        when(players.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(2L, 5L)).isInstanceOf(ApiException.class).hasMessageContaining("roster");
        assertThatThrownBy(() -> service.updateTags(1L, 6L, List.of())).hasMessageContaining("roster");
        assertThatThrownBy(() -> service.deactivate(1L, 7L)).hasMessageContaining("no encontrado");
    }
}
