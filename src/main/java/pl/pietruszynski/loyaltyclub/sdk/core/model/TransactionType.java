package pl.pietruszynski.loyaltyclub.sdk.core.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * Rodzaj operacji punktowej zarejestrowanej w programie. Ten sam zbior wartosci
 * wraca z {@code /api/store/transactions/**} i z historii klienta w {@code /api/ecom}.
 */
public enum TransactionType {

    /** Sprzedaz w kasie lub w sklepie internetowym. */
    SALE,

    /** Zwrot towaru; cofa punkty naliczone przy sprzedazy zrodlowej. */
    RETURN,

    /** Reczna korekta salda wykonana przez administratora. */
    MANUAL_ADJUSTMENT,

    /** Premia za polecenie — dla polecajacego i dla poleconego. */
    REFERRAL,

    /** Pobranie punktow przy wydaniu kuponu. */
    POINTS_REDEMPTION,

    /** Zwrot punktow przy anulowaniu wydanego kuponu. */
    POINTS_REFUND,

    /** Wartosc nieznana temu wydaniu SDK — nowszy backend dodal kolejny rodzaj. */
    @JsonEnumDefaultValue
    UNKNOWN;

    /**
     * Operacje bez okresu karencji i bez daty wygasniecia: punkty sa dostepne od chwili
     * zapisu. Odwzorowuje {@code TransactionType#isImmediatelyAvailable} z backendu.
     */
    public boolean isImmediatelyAvailable() {
        return this == MANUAL_ADJUSTMENT || this == POINTS_REDEMPTION || this == POINTS_REFUND;
    }

    /**
     * Czy operacja wchodzi do dorobku punktowego wyznaczajacego poziom lojalnosciowy.
     * Operacje kuponowe sa neutralne — klient nie traci statusu za korzystanie z programu.
     */
    public boolean countsTowardsLifetimePoints() {
        return this == SALE || this == RETURN || this == MANUAL_ADJUSTMENT || this == REFERRAL;
    }
}
