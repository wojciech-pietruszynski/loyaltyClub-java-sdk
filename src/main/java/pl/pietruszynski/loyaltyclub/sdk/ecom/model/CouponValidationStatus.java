package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Wynik walidacji kuponu. Backend zwraca go w odpowiedzi 200 nawet wtedy, gdy kupon
 * jest nie do przyjecia — o odrzuceniu koszyka decyduje ta wartosc, nie kod HTTP.
 */
public enum CouponValidationStatus {

    /** Kupon mozna zrealizowac. */
    VALID,

    COUPON_NOT_FOUND,
    CUSTOMER_NOT_FOUND,

    /** Kupon nalezy do innego klienta. */
    COUPON_BELONGS_TO_ANOTHER_ACCOUNT,

    COUPON_ALREADY_USED,
    COUPON_EXPIRED,

    /** Kupon wycofany przez operatora — pomylka przy wydaniu albo reklamacja. */
    COUPON_CANCELLED,

    /** Konto uczestnika zawieszone albo zanonimizowane; kupon zostaje niewykorzystany. */
    CUSTOMER_NOT_ACTIVE,

    /** Wartosc nieznana temu wydaniu SDK — potraktuj jak odmowe. */
    @JsonEnumDefaultValue
    UNKNOWN;

    /** Czy kupon nadaje sie do realizacji. */
    public boolean isValid() {
        return this == VALID;
    }
}
