package cl.chessquery.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ResourceServerAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DispatcherServletAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class, SecurityAutoConfiguration.class,
                    OAuth2ResourceServerAutoConfiguration.class, ResourceServerAutoConfiguration.class))
            .withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/issuer");

    @Configuration
    static class Stubs {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
    }

    @Configuration
    static class LocalResolver {
        @Bean PlayerIdentityResolver localPlayerIdentityResolver() {
            return (sub, claims) -> new PlayerIdentityResolver.ResolvedIdentity(1L, null);
        }
    }

    @Test
    void withUsersUrlUsesHttpResolverAndWiresSecurity() {
        runner.withUserConfiguration(Stubs.class)
              .withPropertyValues("chessquery.auth.users-url=http://users:8081",
                                  "chessquery.auth.internal-token=abc")
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
                  assertThat(ctx).hasSingleBean(CurrentUserArgumentResolver.class);
                  assertThat(ctx).hasBean("currentUserWebMvcConfigurer");
                  assertThat(ctx.getBean(PlayerIdentityResolver.class)).isInstanceOf(HttpPlayerIdentityResolver.class);
                  AuthProperties props = ctx.getBean(AuthProperties.class);
                  assertThat(props.usersUrl()).isEqualTo("http://users:8081");
                  assertThat(props.rolesClaim()).isEqualTo("roles");
                  assertThat(ctx.getBean("currentUserWebMvcConfigurer")).isInstanceOf(WebMvcConfigurer.class);
              });
    }

    @Test
    void userProvidedResolverWins() {
        runner.withUserConfiguration(Stubs.class, LocalResolver.class)
              .run(ctx -> {
                  assertThat(ctx).hasSingleBean(PlayerIdentityResolver.class);
                  assertThat(ctx.getBean(PlayerIdentityResolver.class)).isNotInstanceOf(HttpPlayerIdentityResolver.class);
                  assertThat(ctx.getBean(AuthProperties.class).internalToken()).isEmpty();
              });
    }

    @Test
    void propertiesDefaults() {
        AuthProperties p = new AuthProperties(null, null, " ", null);
        assertThat(p.usersUrl()).isEmpty();
        assertThat(p.originSecret()).isEmpty();
        assertThat(p.internalToken()).isEmpty();
        assertThat(p.rolesClaim()).isEqualTo("roles");
        UserPrincipal u = new UserPrincipal(1, "s", "e", null, java.util.Set.of());
        assertThat(u.isOrganizer()).isFalse();
        assertThat(Map.of()).isEmpty();
    }

    /** El cliente interno manda el token y, solo si está configurada, la cabecera de origen del ALB. */
    @Test
    void internalClientHeaders() {
        for (String origin : new String[] {"", "secreto-origen"}) {
            AuthProperties props = new AuthProperties("http://users", "tok", "roles", origin);
            Map<String, java.util.List<String>> sent = new java.util.HashMap<>();
            org.springframework.web.client.RestClient client = InternalHttp.usersClient(props).mutate()
                    .requestInterceptor((req, body, exec) -> {
                        sent.putAll(req.getHeaders());
                        throw new java.io.IOException("sin red en la prueba");
                    }).build();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.get().uri("/internal/x").retrieve().toBodilessEntity())
                    .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
            assertThat(sent.get(InternalTokenFilter.HEADER)).containsExactly("tok");
            assertThat(sent.containsKey(InternalHttp.ORIGIN_HEADER)).isEqualTo(!origin.isEmpty());
        }
    }
}
