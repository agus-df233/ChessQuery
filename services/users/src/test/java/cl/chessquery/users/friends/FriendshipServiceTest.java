package cl.chessquery.users.friends;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reglas de negocio de amistad que la integración no cubre (errores y carreras). */
class FriendshipServiceTest {

    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final FriendshipService service = new FriendshipService(friendships, players, mock(EventPublisher.class));

    private static Friendship row(long id, long from, long to, Friendship.Status status) {
        Friendship f = new Friendship(from, to);
        f.setStatus(status);
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }

    @Test
    void requestValidations() {
        assertThatThrownBy(() -> service.request(1L, 1L)).hasMessageContaining("ti mismo");
        when(players.existsById(2L)).thenReturn(false);
        assertThatThrownBy(() -> service.request(1L, 2L)).hasMessageContaining("no encontrado");

        when(players.existsById(2L)).thenReturn(true);
        when(friendships.findByPair(1L, 2L)).thenReturn(Optional.of(row(5, 1, 2, Friendship.Status.ACCEPTED)));
        assertThatThrownBy(() -> service.request(1L, 2L)).hasMessageContaining("Ya son amigos");

        when(friendships.findByPair(1L, 2L)).thenReturn(Optional.empty());
        when(friendships.saveAndFlush(any(Friendship.class))).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> service.request(1L, 2L)).isInstanceOf(ApiException.class).hasMessageContaining("solicitud");
    }

    @Test
    void acceptDeclineAndRemoveGuards() {
        when(friendships.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(9L, 1L)).hasMessageContaining("no existe");
        when(friendships.findById(9L)).thenReturn(Optional.of(row(9, 1, 2, Friendship.Status.ACCEPTED)));
        assertThatThrownBy(() -> service.accept(9L, 2L)).hasMessageContaining("ya fue respondida");
        when(friendships.findById(9L)).thenReturn(Optional.of(row(9, 1, 2, Friendship.Status.PENDING)));
        assertThatThrownBy(() -> service.accept(9L, 1L)).hasMessageContaining("destinatario");
        assertThat(service.accept(9L, 2L).status()).isEqualTo("FRIENDS");

        when(friendships.findByPair(1L, 3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(1L, 3L)).hasMessageContaining("No existe");
        assertThat(service.areFriends(null, 1L)).isFalse();
        assertThat(service.areFriends(1L, 1L)).isFalse();
        assertThat(service.statusWith(1L, 1L).status()).isEqualTo("NONE");
        when(friendships.findByPair(1L, 2L)).thenReturn(Optional.of(row(4, 2, 1, Friendship.Status.PENDING)));
        assertThat(service.statusWith(1L, 2L).status()).isEqualTo("PENDING_IN");
    }
}
