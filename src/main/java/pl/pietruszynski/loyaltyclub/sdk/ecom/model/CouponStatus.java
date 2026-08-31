package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Stan kuponu wydanego klientowi. Backend wylicza go z dat przy kazdym odczycie,
 * wiec kupon po terminie wraca jako {@link #EXPIRED} niezaleznie od tego, co stoi w bazie.
 */
public enum CouponStatus {

    /** Kupon wydany i mozliwy do realizacji. */
    ACTIVE,

    /** Kupon zrealizowany. */
    USED,

    /** Uplynela data waznosci. */
    EXPIRED,

    /** Kupon wycofany przez operatora — pomylka przy wydaniu albo reklamacja. */
    CANCELLED,

    /** Wartosc nieznana temu wydaniu SDK. */
    @JsonEnumDefaultValue
    UNKNOWN;

    /** Stany koncowe: nie zmieniaja sie juz samoczynnie wraz z uplywem czasu. */
    public boolean isFinal() {
        return this == USED || this == CANCELLED;
    }
}
