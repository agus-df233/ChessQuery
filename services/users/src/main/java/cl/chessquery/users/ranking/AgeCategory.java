package cl.chessquery.users.ranking;

import java.time.LocalDate;
import java.time.Period;

/** Categorías por edad del ranking nacional, evaluadas al día de hoy. */
public enum AgeCategory {

    SUB_8(0, 7), SUB_10(8, 9), SUB_12(10, 11), SUB_14(12, 13), SUB_16(14, 15),
    SUB_18(16, 17), SUB_20(18, 19), ADULTO(20, 49), SENIOR(50, Integer.MAX_VALUE);

    private final int minAge;
    private final int maxAge;

    AgeCategory(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    /** ADULTO cuando no hay fecha de nacimiento. */
    public static AgeCategory fromBirthDate(LocalDate birthDate) {
        if (birthDate == null) return ADULTO;
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        for (AgeCategory c : values()) {
            if (age >= c.minAge && age <= c.maxAge) return c;
        }
        return SENIOR;
    }

    /** Fecha de nacimiento más antigua que hoy cae en la categoría (null = sin límite). */
    public LocalDate minBirthDate() {
        return maxAge == Integer.MAX_VALUE ? null : LocalDate.now().minusYears(maxAge + 1L).plusDays(1);
    }

    /** Fecha de nacimiento más reciente que hoy cae en la categoría. */
    public LocalDate maxBirthDate() {
        return LocalDate.now().minusYears(minAge);
    }
}
