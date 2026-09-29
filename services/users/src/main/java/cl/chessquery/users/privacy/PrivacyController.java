package cl.chessquery.users.privacy;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.users.federation.FederationLinkService;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.player.PlayerDtos.PublicProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Derechos del titular sobre sus datos. Siempre sobre "mí" (identidad del token). */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class PrivacyController {

    private final PrivacyService privacy;
    private final FederationLinkService federation;

    public record FederationLinkRequest(String federationId) {}

    /** Acceso y portabilidad: descarga JSON con todo lo que se guarda del titular. */
    @GetMapping("/export")
    public ResponseEntity<PrivacyService.Export> export(@CurrentUser UserPrincipal user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"chessquery-mis-datos.json\"")
                .body(privacy.export(user.playerId()));
    }

    /** Supresión de la cuenta y de sus datos personales. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@CurrentUser UserPrincipal user) {
        privacy.erase(user.playerId());
    }

    /** Vincula mi id federativo y pide al ETL mi ficha (consulta puntual, con mi consentimiento). */
    @PostMapping("/federation-link")
    public Profile linkFederation(@CurrentUser UserPrincipal user, @RequestBody FederationLinkRequest req) {
        return federation.link(user.playerId(), req.federationId());
    }

    /** Reclama una ficha federada sin dueño; exige verificar la identidad (RUT o año de nacimiento + nombre). */
    @PostMapping("/claim")
    public Profile claim(@CurrentUser UserPrincipal user, @RequestBody FederationLinkService.ClaimRequest req) {
        return federation.claim(user.playerId(), req);
    }

    /** "¿Eres tú?": perfiles federados sin dueño con mi nombre. */
    @GetMapping("/claim-suggestions")
    public List<PublicProfile> claimSuggestions(@CurrentUser UserPrincipal user) {
        return privacy.claimSuggestions(user.playerId());
    }
}
