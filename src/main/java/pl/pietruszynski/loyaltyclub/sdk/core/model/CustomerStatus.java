package pl.pietruszynski.loyaltyclub.sdk.core.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** Stan konta uczestnika programu. */
public enum CustomerStatus {

    /** Konto czynne — naliczanie i wymiana punktow dozwolone. */
    ACTIVE,

    /** Konto zawieszone — historia zachowana, operacje punktowe odrzucane. */
    INACTIVE,

    /** Dane osobowe usuniete na zadanie uczestnika; stan nieodwracalny. */
    ANONYMIZED,

    /** Wartosc nieznana temu wydaniu SDK. */
    @JsonEnumDefaultValue
    UNKNOWN;

    /** Czy backend przyjmie dla tego konta sprzedaz, zwrot albo wymiane punktow. */
    public boolean allowsPointOperations() {
        return this == ACTIVE;
    }
}
