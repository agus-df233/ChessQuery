package cl.chessquery.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesInternalCallWithRightToken() throws Exception {
        InternalTokenFilter f = new InternalTokenFilter("s3cr3t");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/players/by-subject/x");
        req.addHeader(InternalTokenFilter.HEADER, "s3cr3t");
        f.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void ignoresWrongTokenAndNonInternalPaths() throws Exception {
        InternalTokenFilter f = new InternalTokenFilter("s3cr3t");
        MockHttpServletRequest bad = new MockHttpServletRequest("GET", "/internal/x");
        bad.addHeader(InternalTokenFilter.HEADER, "nope");
        f.doFilter(bad, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        MockHttpServletRequest pub = new MockHttpServletRequest("GET", "/api/users/me");
        pub.addHeader(InternalTokenFilter.HEADER, "s3cr3t");
        f.doFilter(pub, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void emptyConfiguredTokenNeverAuthenticates() throws Exception {
        InternalTokenFilter f = new InternalTokenFilter("");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/x");
        req.addHeader(InternalTokenFilter.HEADER, "");
        f.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
