package cl.chessquery.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Política común de todos los servicios:
 * <ul>
 *   <li>{@code /api/public/**}, actuator health/info y preflight CORS: anónimo.</li>
 *   <li>{@code /internal/**}: solo con {@code X-Internal-Token}.</li>
 *   <li>{@code chessquery.auth.public-paths}: rutas que valida el propio servicio (p. ej. el handshake {@code /ws}).</li>
 *   <li>Todo lo demás: Bearer JWT del issuer configurado en
 *       {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (+ audiencia).</li>
 * </ul>
 * Sin sesión, sin CSRF (API stateless). CORS lo maneja CloudFront/ALB en cloud y Vite en dev.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AuthProperties.class)
public class ResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityFilterChain chessquerySecurityFilterChain(HttpSecurity http, AuthProperties props) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new InternalTokenFilter(props.internalToken()), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/public/**", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(props.publicPaths().toArray(String[]::new)).permitAll()
                .requestMatchers("/internal/**").hasAuthority(InternalTokenFilter.ROLE)
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> { }));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(PlayerIdentityResolver.class)
    @ConditionalOnProperty(prefix = "chessquery.auth", name = "users-url")
    public PlayerIdentityResolver httpPlayerIdentityResolver(AuthProperties props) {
        return new HttpPlayerIdentityResolver(InternalHttp.usersClient(props));
    }

    @Bean
    @ConditionalOnMissingBean
    public CurrentUserArgumentResolver currentUserArgumentResolver(PlayerIdentityResolver resolver, AuthProperties props) {
        return new CurrentUserArgumentResolver(resolver, props.rolesClaim());
    }

    @Bean
    public WebMvcConfigurer currentUserWebMvcConfigurer(CurrentUserArgumentResolver resolver) {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(resolver);
            }
        };
    }
}
