package pl.pietruszynski.loyaltyclub.sdk.core.exception;

import lombok.Getter;
import pl.pietruszynski.loyaltyclub.sdk.core.model.ProblemDetail;

import java.util.Map;
import java.util.Optional;

/**
 * Backend odpowiedzial kodem bledu HTTP. Niesie surowa odpowiedz oraz sparsowany
 * {@link ProblemDetail}, jesli serwer go zwrocil.
 */
@Getter
public class LoyaltyClubApiException extends LoyaltyClubException {

    private final int statusCode;
    private final transient ProblemDetail problemDetail;
    private final String rawBody;

    public LoyaltyClubApiException(int statusCode, ProblemDetail problemDetail, String rawBody) {
        super(buildMessage(statusCode, problemDetail, rawBody));
        this.statusCode = statusCode;
        this.problemDetail = problemDetail;
        this.rawBody = rawBody;
    }

    /**
     * Komunikat biznesowy z pola {@code detail}; dla bledow biznesowych backend wklada tam
     * tresc wyjatku (np. {@code "sourceTransactionNumber must be unique"}).
     */
    public Optional<String> getDetail() {
        return Optional.ofNullable(problemDetail).map(ProblemDetail::getDetail);
    }

    /** Mapa {@code pole -> komunikat} przy bledzie walidacji; pusta w pozostalych przypadkach. */
    public Map<String, String> getFieldErrors() {
        return problemDetail == null ? Map.of() : problemDetail.getFieldErrors();
    }

    private static String buildMessage(int statusCode, ProblemDetail problemDetail, String rawBody) {
        StringBuilder message = new StringBuilder("LoyaltyClub API zwrocilo HTTP ").append(statusCode);
        if (problemDetail != null && problemDetail.getDetail() != null && !problemDetail.getDetail().isBlank()) {
            message.append(": ").append(problemDetail.getDetail());
            Map<String, String> fieldErrors = problemDetail.getFieldErrors();
            if (!fieldErrors.isEmpty()) {
                message.append(' ').append(fieldErrors);
            }
        } else if (rawBody != null && !rawBody.isBlank()) {
            message.append(": ").append(rawBody.length() > 512 ? rawBody.substring(0, 512) + "..." : rawBody);
        }
        return message.toString();
    }
}
