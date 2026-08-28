package pl.pietruszynski.loyaltyclub.sdk.core.exception;

/**
 * Blad warstwy transportowej: brak polaczenia, timeout, przerwanie watku.
 * Serwer nie zwrocil odpowiedzi HTTP, wiec stan operacji po stronie backendu jest nieznany.
 */
public class LoyaltyClubTransportException extends LoyaltyClubException {

    public LoyaltyClubTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
