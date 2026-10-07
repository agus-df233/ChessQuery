package cl.chessquery.users.ranking;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.player.PlayerTitleRepository;
import cl.chessquery.users.privacy.PublicNames;
import cl.chessquery.users.rating.RatingType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Ranking por ELO nacional o FIDE (standard/rapid/blitz), filtrable por categoría de edad y región. Es una vista para
 * terceros: los menores aparecen con el apellido abreviado ({@link PublicNames}).
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int MAX_LIMIT = 200;

    /** Tipos por los que se puede rankear: el nacional (federación), los tres de FIDE y el ELO ChessQuery por ritmo. */
    public static final Set<RatingType> RANKED = EnumSet.of(
            RatingType.NATIONAL, RatingType.FIDE_STANDARD, RatingType.FIDE_RAPID, RatingType.FIDE_BLITZ,
            RatingType.PLATFORM_BULLET, RatingType.PLATFORM_BLITZ, RatingType.PLATFORM_RAPID,
            RatingType.PLATFORM_CLASSICAL);

    private final PlayerRepository players;
    private final PlayerTitleRepository titles;

    public record Entry(int position, Long playerId, String firstName, String lastName, String currentTitle,
                        String region, String clubName, RatingType ratingType, Integer rating,
                        Integer eloNational, Integer eloFideStandard, String ageCategory) {}

    @Transactional(readOnly = true)
    public List<Entry> ranking(String type, String category, String region, int limit) {
        RatingType ratingType = parseType(type);
        AgeCategory cat = parseCategory(category);
        String reg = region == null || region.isBlank() ? null : region.trim();
        List<Player> rows = players.findRanking(ratingType.name(), reg,
                cat == null ? null : cat.minBirthYear(),
                cat == null ? null : cat.maxBirthYear(),
                PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIMIT))));
        Map<Long, String> titleById = titles.currentTitlesOf(rows.stream().map(Player::getId).toList());
        return IntStream.range(0, rows.size()).mapToObj(i -> {
            Player p = rows.get(i);
            return new Entry(i + 1, p.getId(), p.getFirstName(), PublicNames.lastName(p), titleById.get(p.getId()),
                    p.getRegion(), p.getClub() != null ? p.getClub().getName() : null,
                    ratingType, p.rating(ratingType), p.getEloNational(), p.getEloFideStandard(),
                    AgeCategory.fromBirthYear(p.getBirthYear()).name());
        }).toList();
    }

    private static RatingType parseType(String type) {
        if (type == null || type.isBlank()) return RatingType.NATIONAL;
        try {
            RatingType t = RatingType.valueOf(type.trim().toUpperCase());
            if (RANKED.contains(t)) return t;
        } catch (IllegalArgumentException ignored) {
            // cae al error de abajo
        }
        throw ApiException.badRequest("INVALID_RATING_TYPE", "Tipo de ranking no soportado: " + type);
    }

    private static AgeCategory parseCategory(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return AgeCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CATEGORY", "Categoría desconocida: " + category);
        }
    }
}
