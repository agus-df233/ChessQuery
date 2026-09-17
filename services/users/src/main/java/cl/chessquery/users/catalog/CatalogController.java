package cl.chessquery.users.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Catálogos de solo lectura para formularios (país, club federativo). Requieren sesión. */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CountryRepository countries;
    private final ClubRepository clubs;

    @GetMapping("/countries")
    public List<Country.Dto> countries() {
        return countries.findAllByOrderByNameAsc().stream().map(Country.Dto::of).toList();
    }

    @GetMapping("/clubs")
    public List<Club.Dto> clubs() {
        return clubs.findAllByOrderByNameAsc().stream().map(Club.Dto::of).toList();
    }
}
