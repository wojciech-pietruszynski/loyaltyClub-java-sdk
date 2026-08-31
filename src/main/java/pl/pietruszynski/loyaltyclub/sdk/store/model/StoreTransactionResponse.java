package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionState;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Wynik rejestracji sprzedazy lub zwrotu. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreTransactionResponse {

    Long transactionId;
    Long customerId;
    String customerNumber;
    TransactionType type;
    TransactionState state;

    /** Punkty naliczone (sprzedaz) lub wycofane, ze znakiem ujemnym (zwrot). */
    Integer points;

    BigDecimal amount;

    /** Przelicznik punktow na jednostke waluty uzyty przy naliczeniu. */
    BigDecimal pointsPerCurrency;

    LocalDateTime purchaseTimestamp;

    /** Moment, od ktorego punkty staja sie dostepne do wykorzystania. */
    LocalDateTime availableFrom;

    LocalDateTime expiresAt;
}
