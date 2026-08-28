package pl.pietruszynski.loyaltyclub.sdk.core.auth;

import java.net.http.HttpRequest;

/**
 * Zrodlo poswiadczen dla wywolan API. Implementacja dokleja wlasciwy naglowek
 * {@code Authorization} do kazdego zadania.
 */
public interface AuthenticationProvider {

    /** Dokleja naglowek uwierzytelniajacy do budowanego zadania. */
    void authorize(HttpRequest.Builder requestBuilder);

    /**
     * Wywolywane po odpowiedzi HTTP 401. Implementacja moze uniewaznic zbuforowane
     * poswiadczenia (np. wygasly token) i zglosic, ze zadanie warto powtorzyc.
     *
     * @return {@code true}, jesli po odswiezeniu poswiadczen zadanie ma sens powtorzyc
     */
    default boolean refreshAfterUnauthorized() {
        return false;
    }
}
