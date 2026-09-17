package cl.chessquery.users.api;

import cl.chessquery.auth.PlayerIdentityResolver;
import cl.chessquery.auth.ResourceServerAutoConfiguration;
import cl.chessquery.common.api.ApiErrorAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@org.springframework.test.context.ActiveProfiles("test")
@Import({ResourceServerAutoConfiguration.class, ApiErrorAutoConfiguration.class})
class MeControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean PlayerIdentityResolver identityResolver;

    @Test
    void meReturnsInternalIdentityForOrganizer() throws Exception {
        when(identityResolver.resolve(eq("sub-1"), any()))
                .thenReturn(new PlayerIdentityResolver.ResolvedIdentity(10L, 3L));

        mvc.perform(get("/api/users/me").with(jwt().jwt(j -> j.subject("sub-1")
                        .claim("email", "a@x.cl").claim("roles", List.of("ADMIN")))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.playerId").value(10))
           .andExpect(jsonPath("$.organizationId").value(3))
           .andExpect(jsonPath("$.organizer").value(true))
           .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void meForPlainPlayer() throws Exception {
        when(identityResolver.resolve(eq("sub-2"), any()))
                .thenReturn(new PlayerIdentityResolver.ResolvedIdentity(11L, null));

        mvc.perform(get("/api/users/me").with(jwt().jwt(j -> j.subject("sub-2"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.organizer").value(false))
           .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }
}
