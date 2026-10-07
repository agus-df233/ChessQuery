package cl.chessquery.game.room;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Códigos para entrar a una sala: 6 caracteres de un alfabeto sin los que se confunden al dictarlos o leerlos en un
 * proyector (sin 0/O, 1/I/L). 31^6 ≈ 887 millones de combinaciones, con {@link SecureRandom}: adivinar uno es
 * impracticable con el límite de intentos ({@link JoinAttempts}).
 */
@Component
public class RoomCodes {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int LENGTH = 6;

    private final RandomGenerator random;

    public RoomCodes() {
        this(new SecureRandom());
    }

    RoomCodes(RandomGenerator random) {
        this.random = random;
    }

    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }

    /** Lo que escribe el jugador ("ab3-k9q ") en la forma guardada, o null si no puede ser un código. */
    public static String normalize(String input) {
        if (input == null) return null;
        String code = input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) return null;
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) return null;
        }
        return code;
    }
}
