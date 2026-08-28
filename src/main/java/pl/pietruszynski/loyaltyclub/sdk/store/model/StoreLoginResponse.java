package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/** Odpowiedz logowania sklepu: token JWT wraz z momentem wygasniecia. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreLoginResponse {

    /** Token JWT do naglowka {@code Authorization: Bearer}. */
    String token;

    /** Moment wygasniecia tokenu jako epoch w milisekundach. */
    long expiresAt;

    /** Rola przypisana tokenowi; dla tego endpointu zawsze {@code STORE}. */
    String role;

    /** Kraj uzytkownika; backend zwraca tu {@code null} dla roli STORE. */
    String country;

    /** {@link #expiresAt} jako {@link Instant}. */
    public Instant expiresAtInstant() {
        return Instant.ofEpochMilli(expiresAt);
    }
}
