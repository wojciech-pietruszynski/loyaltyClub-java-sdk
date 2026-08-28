package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Kupon wydany klientowi. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerCoupon {

    Long id;
    String couponCode;
    Long customerId;
    String customerName;
    String country;

    /** Nominal kuponu. */
    BigDecimal couponValue;

    /** Minimalna wartosc koszyka, przy ktorej kupon dziala. */
    BigDecimal minimumPurchaseValue;

    /** Punkty pobrane za wydanie kuponu. */
    Integer requiredPoints;

    Integer validityDays;
    String couponPrefix;

    /** Powod wydania kuponu, np. wymiana punktow albo akcja obslugi klienta. */
    String reason;

    /** Status kuponu nadany przez backend, np. {@code ACTIVE}, {@code USED}, {@code EXPIRED}. */
    String status;

    LocalDateTime issuedAt;
    LocalDateTime expiresAt;
}
