package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/** Stan naliczonych punktow. */
public enum TransactionState {

    /** Punkty naliczone, ale jeszcze nie do wykorzystania. */
    PENDING,

    /** Punkty dostepne do wymiany. */
    AVAILABLE,

    /** Punkty po terminie waznosci. */
    EXPIRED,

    /** Wartosc nieznana temu wydaniu SDK. */
    @JsonEnumDefaultValue
    UNKNOWN
}
