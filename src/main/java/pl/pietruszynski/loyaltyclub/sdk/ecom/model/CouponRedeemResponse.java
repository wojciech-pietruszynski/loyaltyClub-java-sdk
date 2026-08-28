package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

/** Wynik wymiany punktow na kupon. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponRedeemResponse {

    /** Kod kuponu do przekazania klientowi. */
    String couponCode;

    String customerNumber;

    /** Status wydanego kuponu, np. {@code ACTIVE}. */
    String status;

    LocalDateTime issuedAt;
    LocalDateTime expiresAt;

    /** Warunki handlowe kuponu. */
    CouponDefinition definition;
}
