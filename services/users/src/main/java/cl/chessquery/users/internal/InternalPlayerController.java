package cl.chessquery.users.internal;

import cl.chessquery.auth.PlayerIdentityResolver.ResolvedIdentity;
import cl.chessquery.users.player.LocalPlayerIdentityResolver;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Contrato que consumen los demás servicios vía {@code HttpPlayerIdentityResolver}.
 * Protegido por {@code X-Internal-Token}; nunca expuesto por el ALB.
 */
@RestController
@RequestMapping("/internal/players")
@Validated
public class InternalPlayerController {

    private final LocalPlayerIdentityResolver resolver;

    public InternalPlayerController(LocalPlayerIdentityResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/by-subject/{subject}")
    public ResolvedIdentity bySubject(@PathVariable String subject) {
        return resolver.find(subject);
    }

    public record ProvisionRequest(@NotBlank String subject, String email, String firstName,
                                   String lastName, String displayName) {}

    @PostMapping("/provision")
    @ResponseStatus(HttpStatus.CREATED)
    public ResolvedIdentity provision(@RequestBody @jakarta.validation.Valid ProvisionRequest req) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", req.email());
        claims.put("given_name", req.firstName());
        claims.put("family_name", req.lastName());
        claims.put("name", req.displayName());
        return resolver.resolve(req.subject(), claims);
    }
}
