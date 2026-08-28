package pl.pietruszynski.loyaltyclub.sdk.core.http;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

import java.util.Map;

/**
 * Opis pojedynczego wywolania API, niezalezny od warstwy transportowej.
 */
@Getter
@Builder
@ToString
public class ApiRequest {

    private final HttpMethod method;

    /** Sciezka wzgledem bazowego URI, zaczynajaca sie od {@code /}, np. {@code /api/store}. */
    private final String path;

    @Singular("queryParam")
    private final Map<String, String> queryParams;

    @Singular("header")
    private final Map<String, String> headers;

    /** Obiekt do zserializowania jako JSON; {@code null} oznacza zadanie bez ciala. */
    private final Object body;

    /**
     * Czy zadanie mozna bezpiecznie powtorzyc. Ustawiane przez klienta dla kazdej operacji
     * z osobna — GET-y i operacje chronione kluczem idempotentnosci sa bezpieczne,
     * rejestracja sprzedazy i zwrotu nie.
     */
    @Builder.Default
    private final boolean retryable = false;
}
