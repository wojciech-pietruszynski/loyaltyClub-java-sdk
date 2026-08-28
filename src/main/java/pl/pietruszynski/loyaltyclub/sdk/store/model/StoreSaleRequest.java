package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rejestracja sprzedazy — naliczenie punktow za paragon.
 *
 * <p>Backend wymaga, by {@link #totalAmount} po zaokragleniu do dwoch miejsc rownalo sie
 * sumie cen pozycji, a {@link #sourceTransactionNumber} bylo globalnie unikalne. Oba warunki
 * SDK sprawdza lokalnie (pierwszy) albo zglosi jako HTTP 400 (drugi).
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreSaleRequest {

    /** Numer klienta w programie lojalnosciowym, wymagany. */
    String customerNumber;

    /** Pozycje paragonu; lista nie moze byc pusta. */
    @Singular
    List<StoreTransactionItem> items;

    /** Wartosc paragonu; musi byc dodatnia i rowna sumie cen pozycji. */
    BigDecimal totalAmount;

    /** Numer transakcji w systemie zrodlowym (kasa); wymagany i unikalny. */
    String sourceTransactionNumber;

    /** Moment zakupu; gdy pominiety, backend przyjmuje czas biezacy. */
    LocalDateTime purchaseTimestamp;
}
