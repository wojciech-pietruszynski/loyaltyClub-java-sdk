package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

/** Wynik walidacji kuponu przy skladaniu zamowienia. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponValidationResponse {

    /** Werdykt walidacji; sprawdz go przed zastosowaniem rabatu. */
    CouponValidationStatus status;

    String couponCode;
    String customerNumber;

    /** Stan kuponu w bazie; {@code null}, gdy kuponu nie znaleziono. */
    CouponStatus couponStatus;

    LocalDateTime issuedAt;
    LocalDateTime expiresAt;

    /** Warunki handlowe kuponu; {@code null}, gdy kuponu nie znaleziono. */
    CouponDefinition definition;

    /** Skrot na {@code status == VALID}, odporny na {@code null}. */
    public boolean isValid() {
        return status != null && status.isValid();
    }
}
