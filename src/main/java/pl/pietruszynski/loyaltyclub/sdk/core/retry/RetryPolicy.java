package pl.pietruszynski.loyaltyclub.sdk.core.retry;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Polityka ponowien z wykladniczym backoffem i jitterem.
 *
 * <p>Ponawiane sa wylacznie zadania oznaczone jako bezpieczne do powtorzenia
 * (wszystkie GET-y, logowanie oraz realizacja kuponu chroniona naglowkiem
 * {@code Idempotency-Key}). Rejestracja sprzedazy i zwrotu nie jest ponawiana automatycznie:
 * backend wymusza unikalnosc {@code sourceTransactionNumber}, ale przy bledzie sieci
 * po stronie klienta nie wiadomo, czy transakcja zostala juz zapisana.
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class RetryPolicy {

    /** Laczna liczba prob, wliczajac pierwsza. Wartosc 1 wylacza ponawianie. */
    @Builder.Default
    private final int maxAttempts = 3;

    @Builder.Default
    private final Duration initialBackoff = Duration.ofMillis(200);

    @Builder.Default
    private final Duration maxBackoff = Duration.ofSeconds(2);

    @Builder.Default
    private final double multiplier = 2.0d;

    /** Udzial losowego rozrzutu w wyliczonym opoznieniu, z zakresu 0.0-1.0. */
    @Builder.Default
    private final double jitterFactor = 0.2d;

    /** Kody odpowiedzi kwalifikujace sie do ponowienia. */
    @Builder.Default
    private final Set<Integer> retryableStatusCodes = Set.of(408, 425, 429, 500, 502, 503, 504);

    /** Czy ponawiac po bledzie wejscia-wyjscia (zerwane polaczenie, timeout). */
    @Builder.Default
    private final boolean retryOnIoException = true;

    public static RetryPolicy defaultPolicy() {
        return RetryPolicy.builder().build();
    }

    public static RetryPolicy none() {
        return RetryPolicy.builder().maxAttempts(1).retryOnIoException(false).build();
    }

    public boolean isRetryableStatus(int statusCode) {
        return retryableStatusCodes.contains(statusCode);
    }

    /**
     * Opoznienie przed proba numer {@code attempt} (liczac od 1 dla pierwszego ponowienia).
     */
    public Duration backoffBefore(int attempt) {
        double base = initialBackoff.toMillis() * Math.pow(multiplier, Math.max(0, attempt - 1));
        long capped = (long) Math.min(base, maxBackoff.toMillis());
        if (jitterFactor > 0) {
            long spread = (long) (capped * jitterFactor);
            if (spread > 0) {
                capped = capped - spread + ThreadLocalRandom.current().nextLong(2 * spread + 1);
            }
        }
        return Duration.ofMillis(Math.max(0, capped));
    }
}
