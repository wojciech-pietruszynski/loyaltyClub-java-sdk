package pl.pietruszynski.loyaltyclub.sdk.core.exception;

import pl.pietruszynski.loyaltyclub.sdk.core.model.ProblemDetail;

/**
 * HTTP 400. Backend uzywa tego kodu zarowno dla bledow bean validation
 * (wtedy wypelnione jest {@link #getFieldErrors()}), jak i dla wszystkich bledow biznesowych
 * (np. przekroczona kwota zwrotu, niepowtarzalny numer transakcji) — wtedy liczy sie
 * {@link #getDetail()}.
 */
public class BadRequestException extends LoyaltyClubApiException {

    public BadRequestException(ProblemDetail problemDetail, String rawBody) {
        super(400, problemDetail, rawBody);
    }
}
