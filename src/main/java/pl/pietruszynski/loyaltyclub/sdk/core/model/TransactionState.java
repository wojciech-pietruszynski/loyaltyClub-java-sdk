package pl.pietruszynski.loyaltyclub.sdk.core.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** Stan naliczonych punktow. Backend wylicza go z dat przy kazdym odczycie. */
public enum TransactionState {

    /** Punkty naliczone, ale jeszcze nie do wykorzystania (okres karencji). */
    PENDING,

    /** Punkty dostepne do wymiany. */
    AVAILABLE,

    /** Punkty po terminie waznosci. */
    EXPIRED,

    /** Wartosc nieznana temu wydaniu SDK. */
    @JsonEnumDefaultValue
    UNKNOWN
}
