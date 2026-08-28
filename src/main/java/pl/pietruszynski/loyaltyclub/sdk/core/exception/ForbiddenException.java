package pl.pietruszynski.loyaltyclub.sdk.core.exception;

import pl.pietruszynski.loyaltyclub.sdk.core.model.ProblemDetail;

/** HTTP 403 — poswiadczenia poprawne, ale rola nie ma dostepu do zasobu (np. ECOM na /api/store). */
public class ForbiddenException extends LoyaltyClubApiException {

    public ForbiddenException(ProblemDetail problemDetail, String rawBody) {
        super(403, problemDetail, rawBody);
    }
}
