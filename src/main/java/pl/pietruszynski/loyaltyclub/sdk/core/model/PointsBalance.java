package pl.pietruszynski.loyaltyclub.sdk.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Saldo punktow klienta w rozbiciu na stany naliczenia. Ten sam ksztalt zwracaja
 * {@code GET /api/store/customers/{customerNumber}/points} oraz odpowiednik w {@code /api/ecom}.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointsBalance {

    Long customerId;
    String customerNumber;

    /** Punkty naliczone, ale jeszcze niedostepne do wykorzystania (okres karencji). */
    Integer pendingPoints;

    /** Punkty gotowe do wymiany na kupon. */
    Integer availablePoints;

    /** Punkty, ktore juz wygasly. */
    Integer expiredPoints;
}
