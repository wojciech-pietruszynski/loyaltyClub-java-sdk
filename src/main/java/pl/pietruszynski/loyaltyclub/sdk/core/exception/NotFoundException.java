package pl.pietruszynski.loyaltyclub.sdk.core.exception;

import pl.pietruszynski.loyaltyclub.sdk.core.model.ProblemDetail;

/** HTTP 404 — zasob nie istnieje, np. nieznany {@code customerNumber}. */
public class NotFoundException extends LoyaltyClubApiException {

    public NotFoundException(ProblemDetail problemDetail, String rawBody) {
        super(404, problemDetail, rawBody);
    }
}
