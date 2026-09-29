package cl.chessquery.users.player;

import cl.chessquery.users.catalog.Club;
import cl.chessquery.users.catalog.Country;
import cl.chessquery.users.privacy.PublicNames;
import cl.chessquery.users.ranking.AgeCategory;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Contratos REST del jugador. Tres proyecciones con propósito distinto:
 * <ul>
 *   <li>{@link Profile}: completa, con datos personales. Solo para el propio jugador y para
 *       el organizador sobre sus provisorios.</li>
 *   <li>{@link PublicProfile}: lo que cualquier otro jugador puede ver. Es una lista blanca:
 *       nada de RUT, email, fecha de nacimiento ni género (hay menores en la plataforma y
 *       los ids son correlativos); a los menores se les abrevia el apellido ({@link PublicNames}).</li>
 *   <li>{@link Summary}: mínimo para que otros servicios pinten nombres y ELO en listas.</li>
 * </ul>
 */
public final class PlayerDtos {

    private PlayerDtos() {}

    /** Usernames de Lichess/Chess.com: van al path de una URL externa, así que solo caracteres seguros. Vacío = desvincular. */
    static final String USERNAME = "^$|^[A-Za-z0-9_-]{2,30}$";
    static final String USERNAME_MSG = "Username inválido: 2 a 30 letras, números, '_' o '-'";

    public record Ratings(
            Integer national, Integer fideStandard, Integer fideRapid, Integer fideBlitz, Integer platform,
            Integer lichessBullet, Integer lichessBlitz, Integer lichessRapid, Integer lichessClassical,
            Integer chesscomBullet, Integer chesscomBlitz, Integer chesscomRapid, Integer chesscomDaily) {

        static Ratings of(Player p) {
            return new Ratings(p.getEloNational(), p.getEloFideStandard(), p.getEloFideRapid(), p.getEloFideBlitz(),
                    p.getEloPlatform(), p.getEloLichessBullet(), p.getEloLichessBlitz(), p.getEloLichessRapid(),
                    p.getEloLichessClassical(), p.getEloChesscomBullet(), p.getEloChesscomBlitz(),
                    p.getEloChesscomRapid(), p.getEloChesscomDaily());
        }
    }

    public record Profile(
            Long id, String firstName, String lastName, String displayName, String email, String rut,
            LocalDate birthDate, String gender, String region, Country.Dto country, Club.Dto club,
            String fideId, String federationId, String lichessUsername, String chesscomUsername,
            Ratings ratings, String currentTitle, String ageCategory,
            String enrichmentSource, Instant enrichedAt,
            boolean provisional, Long createdByOrganizerId, boolean active, List<String> tags,
            Instant createdAt, Instant updatedAt) {

        public static Profile of(Player p, String title) {
            return new Profile(p.getId(), p.getFirstName(), p.getLastName(), p.getDisplayName(), p.getEmail(),
                    p.getRut(), p.getBirthDate(), p.getGender(), p.getRegion(),
                    Country.Dto.of(p.getCountry()), Club.Dto.of(p.getClub()),
                    p.getFideId(), p.getFederationId(), p.getLichessUsername(), p.getChesscomUsername(),
                    Ratings.of(p), title, AgeCategory.fromBirthYear(p.getBirthYear()).name(),
                    p.getEnrichmentSource(), p.getEnrichedAt(),
                    p.isProvisional(), p.getCreatedByOrganizerId(), p.isActive(), p.tagList(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record PublicProfile(
            Long id, String firstName, String lastName, String displayName, String currentTitle,
            String region, Country.Dto country, Club.Dto club, String ageCategory,
            String fideId, String federationId, String lichessUsername, String chesscomUsername,
            Ratings ratings, Instant createdAt) {

        public static PublicProfile of(Player p, String title) {
            return new PublicProfile(p.getId(), p.getFirstName(), PublicNames.lastName(p), PublicNames.displayName(p),
                    title, p.getRegion(), Country.Dto.of(p.getCountry()), Club.Dto.of(p.getClub()),
                    AgeCategory.fromBirthYear(p.getBirthYear()).name(),
                    p.getFideId(), p.getFederationId(), p.getLichessUsername(), p.getChesscomUsername(),
                    Ratings.of(p), p.getCreatedAt());
        }
    }

    /**
     * Resumen para otros servicios (torneos, partidas). {@code publicLastName} ya aplica la abreviatura de menores:
     * es el que se muestra a terceros; {@code lastName} completo queda para el organizador (p. ej. el TRF).
     */
    public record Summary(Long id, String firstName, String lastName, String publicLastName, String currentTitle,
                          String clubName, Integer eloNational, Integer eloFideStandard, Integer eloPlatform,
                          String fideId, String federationId, Integer birthYear, String gender,
                          boolean provisional, Long createdByOrganizerId, boolean hasAccount) {

        public static Summary of(Player p, String title) {
            return new Summary(p.getId(), p.getFirstName(), p.getLastName(), PublicNames.lastName(p), title,
                    p.getClub() != null ? p.getClub().getName() : null,
                    p.getEloNational(), p.getEloFideStandard(), p.getEloPlatform(),
                    p.getFideId(), p.getFederationId(), p.getBirthYear(), p.getGender(),
                    p.isProvisional(), p.getCreatedByOrganizerId(), p.hasAccount());
        }
    }

    public record SearchResult(Long id, String firstName, String lastName, String currentTitle, String clubName,
                               String countryIso, String fideId, Integer eloNational, Integer eloFideStandard,
                               Integer eloPlatform) {

        public static SearchResult of(Player p, String title) {
            return new SearchResult(p.getId(), p.getFirstName(), PublicNames.lastName(p), title,
                    p.getClub() != null ? p.getClub().getName() : null,
                    p.getCountry() != null ? p.getCountry().getIsoCode() : null,
                    p.getFideId(), p.getEloNational(), p.getEloFideStandard(), p.getEloPlatform());
        }
    }

    /** Edición del propio perfil. Campos null = no tocar; cadena vacía = borrar (donde aplica). */
    public record UpdateProfileRequest(
            @Size(min = 1, max = 100) String firstName,
            @Size(min = 1, max = 100) String lastName,
            @Size(max = 200) String displayName,
            @Size(max = 12) String rut,
            LocalDate birthDate,
            @Size(max = 1) String gender,
            Integer countryId,
            Integer clubId,
            @Size(max = 100) String region,
            @Pattern(regexp = USERNAME, message = USERNAME_MSG) String lichessUsername,
            @Pattern(regexp = USERNAME, message = USERNAME_MSG) String chesscomUsername) {}
}
