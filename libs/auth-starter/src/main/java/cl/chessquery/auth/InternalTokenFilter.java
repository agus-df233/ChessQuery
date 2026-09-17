package cl.chessquery.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Autentica llamadas servicio→servicio a {@code /internal/**} con un secreto compartido
 * en la cabecera {@code X-Internal-Token} (comparación en tiempo constante). Los servicios
 * nunca se exponen fuera de la VPC; esto es defensa en profundidad, no el único control.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    public static final String ROLE = "ROLE_INTERNAL";

    private final byte[] expected;

    public InternalTokenFilter(String token) {
        this.expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (expected.length > 0 && provided != null
                && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
            var auth = new UsernamePasswordAuthenticationToken("internal", null,
                    List.of(new SimpleGrantedAuthority(ROLE)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
