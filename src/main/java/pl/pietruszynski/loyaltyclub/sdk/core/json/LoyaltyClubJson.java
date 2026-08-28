package pl.pietruszynski.loyaltyclub.sdk.core.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Fabryka {@link ObjectMapper} skonfigurowanego pod kontrakt backendu LoyaltyClub.
 *
 * <p>Backend to Spring Boot z domyslna konfiguracja Jacksona, wiec:
 * <ul>
 *   <li>{@code LocalDateTime} jedzie jako ISO-8601 bez strefy ({@code 2026-08-28T21:48:00}),
 *       nie jako tablica liczb — stad wylaczone {@code WRITE_DATES_AS_TIMESTAMPS};</li>
 *   <li>nieznane pola w odpowiedzi sa ignorowane, zeby nowsze API nie psulo starszego SDK;</li>
 *   <li>nieznane wartosci enumow mapuja sie na wariant {@code UNKNOWN} zamiast rzucac wyjatkiem;</li>
 *   <li>pola {@code null} nie sa wysylane — backend traktuje brak pola jak wartosc domyslna
 *       (np. {@code purchaseTimestamp}).</li>
 * </ul>
 */
public final class LoyaltyClubJson {

    private LoyaltyClubJson() {
    }

    public static ObjectMapper createDefault() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS))
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .build();
    }
}
