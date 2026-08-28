package pl.pietruszynski.loyaltyclub.sdk.core.exception;

/**
 * Wspolny nadtyp wszystkich bledow zglaszanych przez SDK. Pozwala zlapac cala rodzine
 * jednym catch-em, bez wiazania sie z konkretna przyczyna niepowodzenia.
 */
public class LoyaltyClubException extends RuntimeException {

    public LoyaltyClubException(String message) {
        super(message);
    }

    public LoyaltyClubException(String message, Throwable cause) {
        super(message, cause);
    }
}
