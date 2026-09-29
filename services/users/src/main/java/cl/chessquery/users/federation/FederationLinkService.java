package cl.chessquery.users.federation;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.player.PlayerTitleRepository;
import cl.chessquery.users.privacy.IdentifierHasher;
import cl.chessquery.users.rating.RatingHistoryRepository;
import cl.chessquery.users.rating.RatingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Une la cuenta de un jugador con su registro federativo, siempre por decisión del titular.
 * <ul>
 *   <li><b>Vincular</b> ({@link #link}): el jugador declara su id federativo; se guarda en su cuenta y se pide al ETL
 *       una consulta puntual (evento {@code federation.lookup.requested}). La base legal es su consentimiento.</li>
 *   <li><b>Reclamar</b> ({@link #claim}): si esa ficha ya existe en ChessQuery sin dueño (llegó por FIDE o por la
 *       Federación), se une a la cuenta solo si se <b>verifica la identidad</b>: el RUT que entrega coincide con el hash
 *       de la ficha o, en fichas sin RUT (FIDE), coinciden año de nacimiento y nombre.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FederationLinkService {

    /** Modalidades que vienen de la ficha federada y pasan a la cuenta al reclamarla. */
    private static final Set<RatingType> FEDERATED = EnumSet.of(
            RatingType.NATIONAL, RatingType.FIDE_STANDARD, RatingType.FIDE_RAPID, RatingType.FIDE_BLITZ);

    private final PlayerRepository players;
    private final RatingHistoryRepository history;
    private final PlayerTitleRepository titles;
    private final IdentifierHasher hasher;
    private final EventPublisher events;

    public record ClaimRequest(Long playerId, String federationId, String rut) {}

    @Transactional
    public Profile link(Long playerId, String federationId) {
        String fid = requireFederationId(federationId);
        Player me = require(playerId);
        players.findByFederationId(fid).filter(o -> !o.getId().equals(playerId)).ifPresent(holder -> {
            throw holder.hasAccount()
                    ? ApiException.conflict("FEDERATION_ID_TAKEN", "Esa ficha federativa ya está vinculada a otra cuenta")
                    : ApiException.conflict("CLAIM_REQUIRED", "Esa ficha ya está en ChessQuery: reclámala para unirla a tu cuenta");
        });
        me.setFederationId(fid);
        players.save(me);
        events.publish(UsersEvents.FEDERATION_LOOKUP_REQUESTED, Map.of("playerId", playerId, "federationId", fid));
        log.info("Jugador {} vinculó la ficha federativa {}; consulta puntual solicitada", playerId, fid);
        return Profile.of(me, titles.currentTitleOf(playerId));
    }

    @Transactional
    public Profile claim(Long playerId, ClaimRequest req) {
        Player me = require(playerId);
        Player target = findTarget(req);
        if (target.getId().equals(playerId) || target.hasAccount() || target.isProvisional() || !target.isActive()) {
            throw ApiException.conflict("NOT_CLAIMABLE", "Ese perfil no se puede reclamar");
        }
        if (!identityVerified(me, target, req.rut())) {
            throw ApiException.conflict("IDENTITY_NOT_VERIFIED",
                    "No pudimos verificar que la ficha es tuya: revisa tu RUT o completa tu fecha de nacimiento");
        }
        merge(target, me, req.rut());
        events.publish(UsersEvents.PLAYER_MERGED, Map.of("fromPlayerId", target.getId(), "intoPlayerId", playerId));
        log.info("Jugador {} reclamó la ficha {}", playerId, target.getId());
        return Profile.of(me, titles.currentTitleOf(playerId));
    }

    private Player findTarget(ClaimRequest req) {
        if (req.playerId() != null) return require(req.playerId());
        return players.findByFederationId(requireFederationId(req.federationId()))
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "No hay una ficha con ese id federativo"));
    }

    /** RUT entregado = hash de la ficha; o, si la ficha no tiene RUT (FIDE), mismo año de nacimiento y nombre. */
    boolean identityVerified(Player me, Player target, String rut) {
        if (target.getRutHash() != null) {
            return rut != null && target.getRutHash().equals(hasher.rut(rut));
        }
        return me.getBirthYear() != null && me.getBirthYear().equals(target.getBirthYear()) && sameName(me, target);
    }

    /** Todas las palabras del nombre de la cuenta aparecen en la ficha (la ficha suele traer los dos apellidos). */
    static boolean sameName(Player me, Player target) {
        List<String> mine = words(me.fullName());
        List<String> theirs = words(target.fullName());
        return !mine.isEmpty() && mine.get(0).equals(theirs.isEmpty() ? null : theirs.get(0)) && theirs.containsAll(mine);
    }

    private static List<String> words(String name) {
        String plain = Normalizer.normalize(Objects.toString(name, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(plain.split("[^a-z0-9]+")).filter(w -> !w.isBlank()).toList();
    }

    /** La ficha cede identificadores, ratings federados, historial y títulos; queda inactiva (no se borra por integridad). */
    private void merge(Player from, Player into, String rut) {
        String fed = from.getFederationId(), fide = from.getFideId(), rutHash = from.getRutHash();
        from.setFederationId(null);
        from.setFideId(null);
        from.setRutHash(null);
        from.setActive(false);
        players.saveAndFlush(from); // libera los índices únicos antes de asignarlos a la cuenta

        if (into.getFederationId() == null) into.setFederationId(fed);
        if (into.getFideId() == null) into.setFideId(fide);
        if (rutHash != null) { into.setRutHash(rutHash); into.setRut(rut); }
        if (into.getBirthYear() == null) into.setBirthYear(from.getBirthYear());
        if (into.getClub() == null) into.setClub(from.getClub());
        into.setSourceUrl(from.getSourceUrl());
        into.setSourcePeriod(from.getSourcePeriod());
        FEDERATED.stream().filter(t -> from.rating(t) != null).forEach(t -> into.setRating(t, from.rating(t)));
        players.save(into);
        history.reassign(from.getId(), into.getId());
        titles.reassign(from.getId(), into.getId());
    }

    private static String requireFederationId(String federationId) {
        String fid = federationId == null ? "" : federationId.trim();
        if (!fid.matches("\\d{1,10}")) throw ApiException.badRequest("INVALID_FEDERATION_ID", "El id federativo es numérico");
        return fid;
    }

    private Player require(Long id) {
        return players.findById(id).orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "Jugador " + id + " no encontrado"));
    }
}
