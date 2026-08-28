package pl.pietruszynski.loyaltyclub.sdk.core.exception;

import pl.pietruszynski.loyaltyclub.sdk.core.model.ProblemDetail;

/** HTTP 401 — brak lub niewazne poswiadczenia (wygasly token JWT, zle haslo Basic). */
public class UnauthorizedException extends LoyaltyClubApiException {

    public UnauthorizedException(ProblemDetail problemDetail, String rawBody) {
        super(401, problemDetail, rawBody);
    }
}
