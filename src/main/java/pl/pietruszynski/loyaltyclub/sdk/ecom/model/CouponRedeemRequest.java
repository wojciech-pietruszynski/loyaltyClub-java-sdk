package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Zadanie wymiany punktow klienta na kupon z podanego szablonu. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponRedeemRequest {

    /** Numer klienta, wymagany. */
    String customerNumber;

    /** Identyfikator szablonu kuponu, wymagany. */
    Long couponTemplateId;
}
