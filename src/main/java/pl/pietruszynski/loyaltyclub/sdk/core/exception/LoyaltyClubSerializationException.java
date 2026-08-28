package pl.pietruszynski.loyaltyclub.sdk.core.exception;

/**
 * Nie udalo sie zserializowac zadania lub zdeserializowac odpowiedzi.
 */
public class LoyaltyClubSerializationException extends LoyaltyClubException {

    public LoyaltyClubSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
