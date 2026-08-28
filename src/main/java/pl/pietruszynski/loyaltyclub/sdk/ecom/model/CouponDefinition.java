package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/** Warunki handlowe kuponu, przepisane z szablonu w momencie wydania. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponDefinition {

    Long couponTemplateId;

    /** Nominal kuponu. */
    BigDecimal couponValue;

    /** Minimalna wartosc koszyka, przy ktorej kupon dziala. */
    BigDecimal minimumPurchaseValue;

    /** Punkty potrzebne do wymiany. */
    Integer requiredPoints;

    Integer validityDays;
    String couponPrefix;
    String country;
}
