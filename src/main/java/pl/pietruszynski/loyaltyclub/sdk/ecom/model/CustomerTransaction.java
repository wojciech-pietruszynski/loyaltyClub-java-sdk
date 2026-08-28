package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

/** Pozycja historii punktowej klienta. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerTransaction {

    Long id;

    /** Punkty naliczone (dodatnie) lub wycofane (ujemne). */
    Integer points;

    String description;

    LocalDateTime timestamp;

    /** Moment, od ktorego punkty sa dostepne do wykorzystania. */
    LocalDateTime availableFrom;
}
