package cl.chessquery.auth;

import cl.chessquery.common.api.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserArgumentResolverTest {

    private final PlayerIdentityResolver ids = (sub, claims) ->
            new PlayerIdentityResolver.ResolvedIdentity(42L, "org-owner".equals(sub) ? 7L : null);
    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(ids, "roles");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwt(String sub, List<String> roles) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject(sub).claim("email", sub + "@x.cl")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (roles != null) b.claim("roles", roles);
        return b.build();
    }

    @Test
    void mapsJwtToPrincipalWithOrganizationAndRoles() {
        UserPrincipal p = resolver.fromJwt(jwt("org-owner", List.of("admin")));
        assertThat(p.playerId()).isEqualTo(42L);
        assertThat(p.email()).isEqualTo("org-owner@x.cl");
        assertThat(p.isOrganizer()).isTrue();
        assertThat(p.isAdmin()).isTrue();
    }

    @Test
    void plainPlayerHasNoOrganizationNorRoles() {
        UserPrincipal p = resolver.fromJwt(jwt("someone", null));
        assertThat(p.isOrganizer()).isFalse();
        assertThat(p.isAdmin()).isFalse();
        assertThat(p.roles()).isEmpty();
    }

    @Test
    void resolvesFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt("someone", null)));
        Object result = resolver.resolveArgument(null, null, null, null);
        assertThat(result).isInstanceOf(UserPrincipal.class);
    }

    @Test
    void rejectsWhenNoJwtInContext() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("token");
    }
}
