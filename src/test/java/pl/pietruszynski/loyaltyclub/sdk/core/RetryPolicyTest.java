package pl.pietruszynski.loyaltyclub.sdk.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.pietruszynski.loyaltyclub.sdk.core.retry.RetryPolicy;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    @Test
    @DisplayName("domyslna polityka ponawia typowe bledy przejsciowe, ale nie bledy klienta")
    void defaultPolicyRetriesTransientStatuses() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();

        assertEquals(3, policy.getMaxAttempts());
        assertTrue(policy.isRetryableStatus(503));
        assertTrue(policy.isRetryableStatus(429));
        assertFalse(policy.isRetryableStatus(400));
        assertFalse(policy.isRetryableStatus(404));
    }

    @Test
    @DisplayName("none() wylacza ponawianie calkowicie")
    void nonePolicyDisablesRetries() {
        RetryPolicy policy = RetryPolicy.none();

        assertEquals(1, policy.getMaxAttempts());
        assertFalse(policy.isRetryOnIoException());
    }

    @Test
    @DisplayName("backoff rosnie wykladniczo i zatrzymuje sie na gornym limicie")
    void backoffGrowsAndSaturates() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(400))
                .multiplier(2.0d)
                .jitterFactor(0)
                .build();

        assertEquals(100, policy.backoffBefore(1).toMillis());
        assertEquals(200, policy.backoffBefore(2).toMillis());
        assertEquals(400, policy.backoffBefore(3).toMillis());
        assertEquals(400, policy.backoffBefore(9).toMillis());
    }

    @Test
    @DisplayName("jitter trzyma opoznienie w zadanym przedziale wokol wartosci bazowej")
    void jitterStaysWithinBounds() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(100))
                .jitterFactor(0.2d)
                .build();

        for (int i = 0; i < 200; i++) {
            long millis = policy.backoffBefore(1).toMillis();
            assertTrue(millis >= 80 && millis <= 120, "opoznienie poza przedzialem: " + millis);
        }
    }
}
