package cl.chessquery.users.ranking;

import java.time.LocalDate;
import java.time.Year;

/**
 * Categorías del ranking nacional por <b>año de nacimiento</b>, como las definen FIDE y la
 * federación: cuenta la edad que el jugador cumple en el año en curso (Sub-8 = cumple 8 o menos
 * este año). Así basta el año, que es lo único que se guarda de los federados no reclamados.
 */
public enum AgeCategory {

    SUB_8(0, 8), SUB_10(9, 10), SUB_12(11, 12), SUB_14(13, 14), SUB_16(15, 16),
    SUB_18(17, 18), SUB_20(19, 20), ADULTO(21, 49), SENIOR(50, Integer.MAX_VALUE);

    private final int minAge;
    private final int maxAge;

    AgeCategory(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    /** ADULTO cuando no hay año de nacimiento. */
    public static AgeCategory fromBirthYear(Integer birthYear) {
        if (birthYear == null) return ADULTO;
        int age = Year.now().getValue() - birthYear;
        for (AgeCategory c : values()) {
            if (age >= c.minAge && age <= c.maxAge) return c;
        }
        return SENIOR;
    }

    public static AgeCategory fromBirthDate(LocalDate birthDate) {
        return fromBirthYear(birthDate == null ? null : birthDate.getYear());
    }

    /** Año de nacimiento más antiguo de la categoría (null = sin límite). */
    public Integer minBirthYear() {
        return maxAge == Integer.MAX_VALUE ? null : Year.now().getValue() - maxAge;
    }

    /** Año de nacimiento más reciente de la categoría. */
    public int maxBirthYear() {
        return Year.now().getValue() - minAge;
    }
}
