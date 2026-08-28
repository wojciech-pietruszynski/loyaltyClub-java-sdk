package pl.pietruszynski.loyaltyclub.sdk.core.exception;

import pl.pietruszynski.loyaltyclub.sdk.core.model.ProblemDetail;

/** HTTP 5xx — blad po stronie backendu; zgodnie z polityka retry zostal juz ponowiony. */
public class ServerException extends LoyaltyClubApiException {

    public ServerException(int statusCode, ProblemDetail problemDetail, String rawBody) {
        super(statusCode, problemDetail, rawBody);
    }
}
