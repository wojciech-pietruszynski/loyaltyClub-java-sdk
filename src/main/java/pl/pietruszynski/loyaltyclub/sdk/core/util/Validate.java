package pl.pietruszynski.loyaltyclub.sdk.core.util;

import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubValidationException;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Walidacja po stronie klienta. Odwzorowuje regularne ograniczenia backendu, zeby
 * oczywisty blad kosztowal wyjatek lokalny zamiast round-tripu zakonczonego HTTP 400.
 */
public final class Validate {

    private Validate() {
    }

    public static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new LoyaltyClubValidationException(name + " jest wymagane i nie moze byc puste");
        }
        return value.trim();
    }

    public static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new LoyaltyClubValidationException(name + " jest wymagane");
        }
        return value;
    }

    public static <T> Collection<T> requireNotEmpty(Collection<T> value, String name) {
        if (value == null || value.isEmpty()) {
            throw new LoyaltyClubValidationException(name + " jest wymagane i nie moze byc puste");
        }
        return value;
    }

    public static BigDecimal requirePositive(BigDecimal value, String name) {
        requireNonNull(value, name);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new LoyaltyClubValidationException(name + " musi byc wieksze od zera, bylo: " + value);
        }
        return value;
    }

    public static BigDecimal requireNonNegative(BigDecimal value, String name) {
        requireNonNull(value, name);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new LoyaltyClubValidationException(name + " nie moze byc ujemne, bylo: " + value);
        }
        return value;
    }

    public static void requireState(boolean condition, String message) {
        if (!condition) {
            throw new LoyaltyClubValidationException(message);
        }
    }
}
