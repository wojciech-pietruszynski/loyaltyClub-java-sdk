package pl.pietruszynski.loyaltyclub.sdk.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * Odpowiedz logowania i odswiezenia tokenu: JWT wraz z momentem wygasniecia.
 * Wspolna dla {@code /api/store/auth} i {@code /api/ecom/auth} — backend zwraca
 * z obu ten sam rekord.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponse {

    /** Token JWT do naglowka {@code Authorization: Bearer}. */
    String token;

    /** Moment wygasniecia tokenu jako epoch w milisekundach. */
    long expiresAt;

    /** Rola zapisana w tokenie, np. {@code STORE} albo {@code ECOM}. */
    String role;

    /** Kraj uzytkownika; backend wypelnia go tylko dla kont technicznych panelu. */
    String country;

    /** {@link #expiresAt} jako {@link Instant}. */
    public Instant expiresAtInstant() {
        return Instant.ofEpochMilli(expiresAt);
    }
}
