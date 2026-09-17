package cl.chessquery.users.player;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.users.organization.Organization;
import cl.chessquery.users.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalPlayerIdentityResolverTest {

    private final PlayerRepository players = mock(PlayerRepository.class);
    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
    private final LocalPlayerIdentityResolver resolver = new LocalPlayerIdentityResolver(players, orgs);

    private static Player player(long id, String sub) {
        Player p = Player.fromIdentity(sub, "e@x.cl", "Ana", "Soto", "Ana Soto");
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    void existingPlayerWithOrganization() {
        when(players.findByExternalSubject("s")).thenReturn(Optional.of(player(1L, "s")));
        Organization org = new Organization(1L, "Club X");
        ReflectionTestUtils.setField(org, "id", 8L);
        when(orgs.findByOwnerPlayerId(1L)).thenReturn(Optional.of(org));

        var id = resolver.resolve("s", Map.of());
        assertThat(id.playerId()).isEqualTo(1L);
        assertThat(id.organizationId()).isEqualTo(8L);
        assertThat(org.getPlan()).isEqualTo(Organization.Plan.FREE);
        assertThat(org.getName()).isEqualTo("Club X");
    }

    @Test
    void provisionsNewPlayerFromClaims() {
        when(players.findByExternalSubject("new")).thenReturn(Optional.empty());
        when(players.saveAndFlush(any(Player.class))).thenAnswer(inv -> {
            Player p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 50L);
            return p;
        });
        when(orgs.findByOwnerPlayerId(50L)).thenReturn(Optional.empty());

        var id = resolver.resolve("new", Map.of("email", "n@x.cl", "given_name", "Nico"));
        assertThat(id.playerId()).isEqualTo(50L);
        assertThat(id.organizationId()).isNull();
    }

    @Test
    void concurrentProvisionFallsBackToWinner() {
        when(players.findByExternalSubject("race"))
                .thenReturn(Optional.empty(), Optional.of(player(7L, "race")));
        when(players.saveAndFlush(any(Player.class))).thenThrow(new DataIntegrityViolationException("dup"));
        when(orgs.findByOwnerPlayerId(7L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve("race", Map.of()).playerId()).isEqualTo(7L);
    }

    @Test
    void findDoesNotProvision() {
        when(players.findByExternalSubject("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.find("ghost")).isInstanceOf(ApiException.class);
        when(players.findByExternalSubject("s")).thenReturn(Optional.of(player(2L, "s")));
        when(orgs.findByOwnerPlayerId(2L)).thenReturn(Optional.empty());
        assertThat(resolver.find("s").playerId()).isEqualTo(2L);
    }

    @Test
    void playerDefaultsWhenClaimsMissing() {
        Player p = Player.fromIdentity("s", null, null, null, null);
        assertThat(p.getFirstName()).isEqualTo("Jugador");
        assertThat(p.getLastName()).isEmpty();
        assertThat(p.isProvisional()).isFalse();
        assertThat(p.getExternalSubject()).isEqualTo("s");
    }
}
