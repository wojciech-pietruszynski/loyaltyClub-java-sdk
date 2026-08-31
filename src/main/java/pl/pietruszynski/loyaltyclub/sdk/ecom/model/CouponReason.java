package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** Powod wydania kuponu. */
public enum CouponReason {

    /** Kupon wydany w zamian za punkty. */
    POINTS_EXCHANGE,

    /** Kupon wydany przez obsluge klienta w ramach reklamacji. */
    COMPLAINT,

    /** Wartosc nieznana temu wydaniu SDK. */
    @JsonEnumDefaultValue
    UNKNOWN
}
