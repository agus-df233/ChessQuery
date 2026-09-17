package cl.chessquery.users.ranking;

import cl.chessquery.common.api.ApiException;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.player.PlayerTitleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Ranking nacional por ELO nacional, filtrable por categoría de edad y región. */
@Service
@RequiredArgsConstructor
public class RankingService {

    public static final int MAX_LIMIT = 200;

    private final PlayerRepository players;
    private final PlayerTitleRepository titles;

    public record Entry(int position, Long playerId, String firstName, String lastName, String currentTitle,
                        String region, String clubName, Integer eloNational, Integer eloFideStandard,
                        String ageCategory) {}

    @Transactional(readOnly = true)
    public List<Entry> ranking(String category, String region, int limit) {
        AgeCategory cat = parseCategory(category);
        String reg = region == null || region.isBlank() ? null : region.trim();
        List<Player> rows = players.findRanking(reg,
                cat == null ? null : cat.minBirthDate(),
                cat == null ? null : cat.maxBirthDate(),
                PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIMIT))));
        Map<Long, String> titleById = titles.currentTitlesOf(rows.stream().map(Player::getId).toList());
        return IntStream.range(0, rows.size()).mapToObj(i -> {
            Player p = rows.get(i);
            return new Entry(i + 1, p.getId(), p.getFirstName(), p.getLastName(), titleById.get(p.getId()),
                    p.getRegion(), p.getClub() != null ? p.getClub().getName() : null,
                    p.getEloNational(), p.getEloFideStandard(),
                    AgeCategory.fromBirthDate(p.getBirthDate()).name());
        }).toList();
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
