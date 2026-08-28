package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/** Cena pozycji paragonu. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemPrice {

    /** Kwota; nie moze byc ujemna. */
    BigDecimal amount;

    /** Kod waluty, np. {@code PLN}. */
    String currency;
}
