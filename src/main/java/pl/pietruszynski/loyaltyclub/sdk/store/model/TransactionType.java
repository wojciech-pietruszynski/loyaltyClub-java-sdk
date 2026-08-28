package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** Rodzaj transakcji punktowej zarejestrowanej w programie lojalnosciowym. */
public enum TransactionType {

    SALE,
    RETURN,
    MANUAL_ADJUSTMENT,

    /** Wartosc nieznana temu wydaniu SDK — nowszy backend dodal kolejny rodzaj. */
    @JsonEnumDefaultValue
    UNKNOWN
}
