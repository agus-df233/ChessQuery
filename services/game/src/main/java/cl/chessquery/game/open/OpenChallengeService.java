package cl.chessquery.game.open;

import cl.chessquery.auth.UserPrincipal;
import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.rating.TimeControlCategory;
import cl.chessquery.game.GameQueries;
import cl.chessquery.game.GameService;
import cl.chessquery.game.api.GameDtos.ColorChoice;
import cl.chessquery.game.api.GameDtos.GameView;
import cl.chessquery.game.domain.Game;
import cl.chessquery.game.open.OpenChallengeDtos.OpenChallengeRequest;
import cl.chessquery.game.open.OpenChallengeDtos.OpenChallengeView;
import cl.chessquery.game.users.UsersClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Desafío abierto: un jugador publica un enlace (o un QR) y lo acepta el primero que entre, aunque no sean amigos.
 * Es la puerta de entrada de alguien nuevo: escanea, entra con su cuenta y juega. El enlace lleva un token aleatorio
 * de 128 bits (no se puede adivinar) y vence si nadie lo acepta en {@code open-challenge-ttl}.
 */
@Service
@RequiredArgsConstructor
public class OpenChallengeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    static final int MAX_OPEN_PER_PLAYER = 3;

    private final OpenChallengeRepository challenges;
    private final GameService games;
    private final UsersClient users;
    private final GameQueries queries;
    private final Clock clock;

    @Value("${chessquery.games.open-challenge-ttl:30m}")
    private Duration ttl;

    @Transactional
    public OpenChallengeView create(UserPrincipal me, OpenChallengeRequest req) {
        if (challenges.findByChallengerIdAndStatusOrderByCreatedAtDesc(me.playerId(), OpenChallenge.Status.OPEN).stream()
                .filter(c -> !expired(c)).count() >= MAX_OPEN_PER_PLAYER) {
            throw ApiException.conflict("TOO_MANY_OPEN_CHALLENGES", "Ya tienes " + MAX_OPEN_PER_PLAYER + " desafíos abiertos");
        }
        OpenChallenge c = new OpenChallenge();
        c.setToken(newToken());
        c.setChallengerId(me.playerId());
        c.setChallengerName(users.player(me.playerId()).publicName());
        c.setMinutes(req.minutes());
        c.setIncrementSeconds(req.incrementSeconds());
        c.setColor(req.color() == null ? ColorChoice.RANDOM : req.color());
        c.setRated(req.rated() == null || req.rated());
        c.setCreatedAt(clock.instant());
        challenges.save(c);
        return view(c, me);
    }

    @Transactional(readOnly = true)
    public OpenChallengeView get(UserPrincipal me, String token) {
        return view(require(token), me);
    }

    @Transactional(readOnly = true)
    public List<OpenChallengeView> mine(UserPrincipal me) {
        return challenges.findByChallengerIdAndStatusOrderByCreatedAtDesc(me.playerId(), OpenChallenge.Status.OPEN)
                .stream().filter(c -> !expired(c)).map(c -> view(c, me)).toList();
    }

    /** El primero que acepta juega; si dos aceptan a la vez, el segundo recibe 409 (bloqueo optimista). */
    @Transactional
    public GameView accept(UserPrincipal me, String token) {
        OpenChallenge c = require(token);
        if (c.getChallengerId() == me.playerId()) {
            throw ApiException.badRequest("SELF_CHALLENGE", "No puedes aceptar tu propio desafío");
        }
        requireOpen(c);
        c.setStatus(OpenChallenge.Status.ACCEPTED);
        try {
            challenges.saveAndFlush(c);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw taken();
        }
        Game g = games.startOpenChallenge(c.getChallengerId(), me.playerId(), c.getColor(), c.getMinutes(),
                c.getIncrementSeconds(), c.isRated());
        c.setGameId(g.getId());
        return queries.view(g.getId());
    }

    @Transactional
    public void cancel(UserPrincipal me, String token) {
        OpenChallenge c = require(token);
        if (c.getChallengerId() != me.playerId()) {
            throw ApiException.forbidden("NOT_YOUR_CHALLENGE", "Solo quien publicó el desafío puede cancelarlo");
        }
        requireOpen(c);
        c.setStatus(OpenChallenge.Status.CANCELLED);
    }

    private void requireOpen(OpenChallenge c) {
        if (expired(c)) throw ApiException.conflict("CHALLENGE_EXPIRED", "Ese desafío ya venció");
        if (c.getStatus() != OpenChallenge.Status.OPEN) throw taken();
    }

    private static ApiException taken() {
        return ApiException.conflict("CHALLENGE_TAKEN", "Ese desafío ya no está disponible");
    }

    private boolean expired(OpenChallenge c) {
        return c.getStatus() == OpenChallenge.Status.OPEN && !clock.instant().isBefore(expiresAt(c));
    }

    private Instant expiresAt(OpenChallenge c) {
        return c.getCreatedAt().plus(ttl);
    }

    private OpenChallenge require(String token) {
        return challenges.findByToken(token == null ? "" : token)
                .orElseThrow(() -> ApiException.notFound("CHALLENGE_NOT_FOUND", "Ese desafío no existe"));
    }

    private OpenChallengeView view(OpenChallenge c, UserPrincipal me) {
        String status = expired(c) ? "EXPIRED" : c.getStatus().name();
        return new OpenChallengeView(c.getToken(), c.getChallengerId(), c.getChallengerName(), c.getMinutes(),
                c.getIncrementSeconds(), TimeControlCategory.of(c.getMinutes() * 60, c.getIncrementSeconds()),
                c.getColor(), c.isRated(), status, c.getGameId(), expiresAt(c), c.getChallengerId() == me.playerId());
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
