package pl.pietruszynski.loyaltyclub.sdk.ecom;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.pietruszynski.loyaltyclub.sdk.core.AbstractApiClient;
import pl.pietruszynski.loyaltyclub.sdk.core.http.ApiRequest;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpMethod;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponRedeemRequest;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponRedeemResponse;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponValidationResponse;

/**
 * Klient API kuponowego {@code /api/coupon/**} — wymiana punktow na kupon i walidacja kuponu
 * przy skladaniu zamowienia. Wymaga konta z rola {@code ECOM}, tak samo jak {@link EcomClient}.
 *
 * <pre>{@code
 * try (CouponClient coupons = CouponClient.builder()
 *         .baseUrl("http://localhost:8089")
 *         .basicAuth("ecom-shop", "haslo")
 *         .build()) {
 *
 *     CouponValidationResponse validation = coupons.validate("PL-ABC123", "CUST-000123");
 *     if (validation.isValid()) {
 *         // zastosuj rabat validation.getDefinition().getCouponValue()
 *     }
 * }
 * }</pre>
 *
 * <p>Instancja jest bezpieczna watkowo.
 */
public class CouponClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/coupon";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final TypeReference<CouponRedeemResponse> REDEEM = new TypeReference<>() {
    };
    private static final TypeReference<CouponValidationResponse> VALIDATION = new TypeReference<>() {
    };

    CouponClient(HttpTransport transport) {
        super(transport);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code POST /api/coupon/redeem-points} — wymienia punkty klienta na kupon z podanego szablonu.
     *
     * <p>Klucz idempotentnosci jest wymagany przez backend i tam realnie deduplikuje zadania:
     * powtorzenie z tym samym kluczem zwraca ten sam kupon zamiast pobierac punkty drugi raz.
     * Dlatego klucz musi byc stabilny dla jednej proby biznesowej — zwykle identyfikator
     * zamowienia lub akcji w sklepie, a nie swiezy UUID przy kazdej probie. Dzieki temu SDK
     * moze bezpiecznie ponowic to wywolanie po bledzie sieci.
     *
     * @param idempotencyKey klucz idempotentnosci, wymagany
     */
    public CouponRedeemResponse redeemPoints(String idempotencyKey, CouponRedeemRequest request) {
        String normalizedKey = Validate.requireText(idempotencyKey, "idempotencyKey");
        Validate.requireNonNull(request, "request");
        Validate.requireText(request.getCustomerNumber(), "customerNumber");
        Validate.requireNonNull(request.getCouponTemplateId(), "couponTemplateId");

        return transport.execute(ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH + "/redeem-points")
                .header(IDEMPOTENCY_KEY_HEADER, normalizedKey)
                .body(request)
                // Bezpieczne do ponowienia: backend deduplikuje po naglowku Idempotency-Key.
                .retryable(true)
                .build(), REDEEM);
    }

    /**
     * {@code GET /api/coupon/validate} — sprawdza, czy kupon nalezy do klienta i da sie go zrealizowac.
     *
     * <p>Kupon nieprawidlowy nie jest bledem HTTP: odpowiedz ma status 200, a werdykt siedzi
     * w {@link CouponValidationResponse#getStatus()}.
     */
    public CouponValidationResponse validate(String couponCode, String customerNumber) {
        return transport.execute(ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH + "/validate")
                .queryParam("couponCode", Validate.requireText(couponCode, "couponCode"))
                .queryParam("customerNumber", Validate.requireText(customerNumber, "customerNumber"))
                .retryable(true)
                .build(), VALIDATION);
    }

    /** Builder klienta kuponowego. */
    public static final class Builder extends EcomClientBuilderSupport<Builder> {

        private Builder() {
        }

        public CouponClient build() {
            return new CouponClient(buildTransport(requireAuthentication()));
        }
    }
}
