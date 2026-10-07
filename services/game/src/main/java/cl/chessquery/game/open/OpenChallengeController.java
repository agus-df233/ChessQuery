package cl.chessquery.game.open;

import cl.chessquery.auth.CurrentUser;
import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.game.api.GameDtos.GameView;
import cl.chessquery.game.open.OpenChallengeDtos.OpenChallengeRequest;
import cl.chessquery.game.open.OpenChallengeDtos.OpenChallengeView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Desafíos abiertos: un enlace (o QR) que acepta el primero que entre. */
@RestController
@RequestMapping("/api/games/open")
@RequiredArgsConstructor
public class OpenChallengeController {

    private final OpenChallengeService challenges;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpenChallengeView create(@CurrentUser UserPrincipal me, @Valid @RequestBody OpenChallengeRequest req) {
        return challenges.create(me, req);
    }

    @GetMapping
    public List<OpenChallengeView> mine(@CurrentUser UserPrincipal me) {
        return challenges.mine(me);
    }

    @GetMapping("/{token}")
    public OpenChallengeView get(@CurrentUser UserPrincipal me, @PathVariable String token) {
        return challenges.get(me, token);
    }

    @PostMapping("/{token}/accept")
    public GameView accept(@CurrentUser UserPrincipal me, @PathVariable String token) {
        return challenges.accept(me, token);
    }

    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@CurrentUser UserPrincipal me, @PathVariable String token) {
        challenges.cancel(me, token);
    }
}
