package cl.chessquery.users.player;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.catalog.ClubRepository;
import cl.chessquery.users.catalog.CountryRepository;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.PlayerDtos.Profile;
import cl.chessquery.users.player.PlayerDtos.PublicProfile;
import cl.chessquery.users.player.PlayerDtos.SearchResult;
import cl.chessquery.users.player.PlayerDtos.Summary;
import cl.chessquery.users.player.PlayerDtos.UpdateProfileRequest;
import cl.chessquery.users.privacy.IdentifierHasher;
import cl.chessquery.users.rating.ExternalRatingsRequests;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Lecturas y edición del perfil del jugador, búsqueda y sincronización de cuentas externas. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    public static final int MAX_SEARCH = 50;

    private final PlayerRepository players;
    private final PlayerTitleRepository titles;
    private final ClubRepository clubs;
    private final CountryRepository countries;
    private final ExternalRatingsRequests externalRatings;
    private final EventPublisher events;
    private final IdentifierHasher hasher;

    // ── Lecturas ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Profile profile(Long id) {
        return Profile.of(require(id), titles.currentTitleOf(id));
    }

    @Transactional(readOnly = true)
    public PublicProfile publicProfile(Long id) {
        return PublicProfile.of(require(id), titles.currentTitleOf(id));
    }

    @Transactional(readOnly = true)
    public Profile profileByEmail(String email) {
        String normalized = Emails.normalize(email);
        if (normalized == null) throw ApiException.badRequest("INVALID_EMAIL", "Falta el email");
        Player p = players.findByEmail(normalized)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "No hay jugador con ese email"));
        return Profile.of(p, titles.currentTitleOf(p.getId()));
    }

    /** Resúmenes en lote para que tournament/game pinten nombres y ELO sin N+1. */
    @Transactional(readOnly = true)
    public List<Summary> summaries(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Player> found = players.findAllById(ids);
        Map<Long, String> title = titles.currentTitlesOf(found.stream().map(Player::getId).toList());
        return found.stream().map(p -> Summary.of(p, title.get(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(String q, int limit) {
        if (q == null || q.isBlank()) throw ApiException.badRequest("INVALID_QUERY", "El parámetro q no puede estar vacío");
        List<Player> found = players.searchFuzzy(q.trim(), hasher.rut(q), Math.max(1, Math.min(limit, MAX_SEARCH)));
        Map<Long, String> title = titles.currentTitlesOf(found.stream().map(Player::getId).toList());
        return found.stream().map(p -> SearchResult.of(p, title.get(p.getId()))).toList();
    }

    // ── Escrituras ───────────────────────────────────────────────────────────

    /** Edita el perfil propio. Solo toca los campos presentes; publica {@code player.updated}. */
    @Transactional
    public Profile updateProfile(Long id, UpdateProfileRequest req) {
        Player p = require(id);
        List<String> changed = new ArrayList<>();
        applyPersonalData(p, req, changed);
        applyCatalog(p, req, changed);
        applyLinkedAccounts(p, req, changed);
        if (!changed.isEmpty()) {
            players.save(p);
            events.publish(UsersEvents.PLAYER_UPDATED, Map.of("playerId", id, "fields", changed));
        }
        // Vinculó (o cambió) una cuenta de Lichess o Chess.com: se piden sus ratings de inmediato
        if (changed.contains("lichessUsername") || changed.contains("chesscomUsername")) externalRatings.requestFor(p);
        return Profile.of(p, titles.currentTitleOf(id));
    }

    private void applyPersonalData(Player p, UpdateProfileRequest req, List<String> changed) {
        apply(changed, "firstName", req.firstName(), p::setFirstName);
        apply(changed, "lastName", req.lastName(), p::setLastName);
        apply(changed, "displayName", req.displayName(), v -> p.setDisplayName(blankToNull(v)));
        apply(changed, "region", req.region(), v -> p.setRegion(blankToNull(v)));
        apply(changed, "birthDate", req.birthDate(), p::setBirthDate);
        apply(changed, "gender", req.gender(), v -> p.setGender(blankToNull(v)));
        if (req.rut() != null) {
            String rut = blankToNull(req.rut());
            String rutHash = hasher.rut(rut);
            ensureNotTakenByOther(p, Optional.ofNullable(rutHash).flatMap(players::findByRutHash),
                    "RUT_TAKEN", "Ese RUT ya pertenece a otro jugador");
            apply(changed, "rut", req.rut(), v -> { p.setRut(rut); p.setRutHash(rutHash); });
        }
    }

    private void applyCatalog(Player p, UpdateProfileRequest req, List<String> changed) {
        if (req.countryId() != null) {
            p.setCountry(countries.findById(req.countryId())
                    .orElseThrow(() -> ApiException.notFound("COUNTRY_NOT_FOUND", "País no encontrado")));
            changed.add("country");
        }
        if (req.clubId() != null) {
            p.setClub(clubs.findById(req.clubId())
                    .orElseThrow(() -> ApiException.notFound("CLUB_NOT_FOUND", "Club no encontrado")));
            changed.add("club");
        }
    }

    private void applyLinkedAccounts(Player p, UpdateProfileRequest req, List<String> changed) {
        if (req.lichessUsername() != null) {
            String u = blankToNull(req.lichessUsername());
            ensureNotTakenByOther(p, Optional.ofNullable(u).flatMap(players::findByLichessUsernameIgnoreCase),
                    "LICHESS_USERNAME_TAKEN", "Ese usuario de Lichess ya está vinculado a otro jugador");
            p.setLichessUsername(u);
            changed.add("lichessUsername");
        }
        if (req.chesscomUsername() != null) {
            String u = blankToNull(req.chesscomUsername());
            ensureNotTakenByOther(p, Optional.ofNullable(u).flatMap(players::findByChesscomUsernameIgnoreCase),
                    "CHESSCOM_USERNAME_TAKEN", "Ese usuario de Chess.com ya está vinculado a otro jugador");
            p.setChesscomUsername(u);
            changed.add("chesscomUsername");
        }
    }

    /** Un identificador (RUT, username) no puede quedar en dos jugadores: 409 si ya lo tiene otro. */
    private static void ensureNotTakenByOther(Player me, Optional<Player> holder, String error, String message) {
        if (holder.filter(o -> !o.getId().equals(me.getId())).isPresent()) {
            throw ApiException.conflict(error, message);
        }
    }

    /**
     * Pide al ETL los ratings actuales de Lichess y Chess.com de las cuentas vinculadas. Asíncrono: los ratings llegan
     * en segundos como {@code rating.updated}; la respuesta es el perfil tal como está.
     */
    @Transactional(readOnly = true)
    public Profile syncExternalRatings(Long id) {
        Player p = require(id);
        externalRatings.requestFor(p);
        return Profile.of(p, titles.currentTitleOf(id));
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    Player require(Long id) {
        return players.findById(id)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "Jugador " + id + " no encontrado"));
    }

    private static <T> void apply(List<String> changed, String field, T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
            changed.add(field);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
