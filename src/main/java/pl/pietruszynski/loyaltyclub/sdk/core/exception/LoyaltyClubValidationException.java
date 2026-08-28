package pl.pietruszynski.loyaltyclub.sdk.core.exception;

/**
 * Zadanie odrzucone lokalnie, przed wyslaniem, bo naruszalo kontrakt API.
 * Nie doszlo do zadnego wywolania sieciowego.
 */
public class LoyaltyClubValidationException extends LoyaltyClubException {

    public LoyaltyClubValidationException(String message) {
        super(message);
    }
}
