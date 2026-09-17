package cl.chessquery.users.internal;

import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.auth.ResourceServerAutoConfiguration;
import cl.chessquery.common.api.ApiErrorAutoConfiguration;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.users.player.LocalPlayerIdentityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalPlayerController.class)
@org.springframework.test.context.ActiveProfiles("test")
@Import({ResourceServerAutoConfiguration.class, ApiErrorAutoConfiguration.class})
@TestPropertySource(properties = "chessquery.auth.internal-token=test-token")
class InternalPlayerControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean LocalPlayerIdentityResolver resolver;

    @Test
    void bySubjectRequiresInternalToken() throws Exception {
        mvc.perform(get("/internal/players/by-subject/abc")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/players/by-subject/abc").header("X-Internal-Token", "wrong"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void bySubjectFoundAndNotFound() throws Exception {
        when(resolver.find("abc")).thenReturn(new ResolvedIdentity(5L, null));
        when(resolver.find("nope")).thenThrow(ApiException.notFound("PLAYER_NOT_FOUND", "x"));

        mvc.perform(get("/internal/players/by-subject/abc").header("X-Internal-Token", "test-token"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value(5));
        mvc.perform(get("/internal/players/by-subject/nope").header("X-Internal-Token", "test-token"))
           .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void provisionCreates() throws Exception {
        when(resolver.resolve(eq("new"), any())).thenReturn(new ResolvedIdentity(99L, null));
        mvc.perform(post("/internal/players/provision").header("X-Internal-Token", "test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"new\",\"email\":\"n@x.cl\",\"firstName\":\"Ana\"}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.playerId").value(99));
    }

    @Test
    void provisionValidatesSubject() throws Exception {
        mvc.perform(post("/internal/players/provision").header("X-Internal-Token", "test-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"n@x.cl\"}"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
