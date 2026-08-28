package pl.pietruszynski.loyaltyclub.sdk.ecom;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.pietruszynski.loyaltyclub.sdk.core.AbstractApiClient;
import pl.pietruszynski.loyaltyclub.sdk.core.http.ApiRequest;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpMethod;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PointsBalance;
import pl.pietruszynski.loyaltyclub.sdk.core.model.ServiceInfo;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Uris;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CustomerTransaction;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.EcomCustomerProfile;

import java.util.List;

/**
 * Klient odczytowego API sklepu internetowego {@code /api/ecom/**} — profil lojalnosciowy,
 * saldo punktow, historia transakcji i kupony klienta. Wymaga konta z rola {@code ECOM}.
 *
 * <p>Naliczanie punktow i zwroty zostaja po stronie kasy ({@code /api/store}); wymiana punktow
 * na kupon i walidacja kuponu maja wlasnego klienta — dostepnego przez {@link #coupons()}.
 *
 * <pre>{@code
 * try (EcomClient ecom = EcomClient.builder()
 *         .baseUrl("http://localhost:8089")
 *         .basicAuth("ecom-shop", "haslo")
 *         .build()) {
 *
 *     EcomCustomerProfile profile = ecom.getCustomerProfile("CUST-000123");
 *     PointsBalance balance = ecom.getPointsBalance("CUST-000123");
 *     CouponValidationResponse validation = ecom.coupons().validate("PL-ABC123", "CUST-000123");
 * }
 * }</pre>
 *
 * <p>Instancja jest bezpieczna watkowo.
 */
public class EcomClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/ecom";

    private static final TypeReference<ServiceInfo> SERVICE_INFO = new TypeReference<>() {
    };
    private static final TypeReference<PointsBalance> POINTS_BALANCE = new TypeReference<>() {
    };
    private static final TypeReference<EcomCustomerProfile> PROFILE = new TypeReference<>() {
    };
    private static final TypeReference<List<CustomerTransaction>> TRANSACTIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<CustomerCoupon>> COUPONS = new TypeReference<>() {
    };

    private final CouponClient couponClient;

    EcomClient(HttpTransport transport) {
        super(transport);
        this.couponClient = new CouponClient(transport);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Klient {@code /api/coupon/**} korzystajacy z tych samych poswiadczen i tej samej puli polaczen.
     * Nie zamykaj go osobno — zamkniecie tego klienta zamyka oba.
     */
    public CouponClient coupons() {
        return couponClient;
    }

    /**
     * {@code GET /api/ecom} — metadane integracji: wersja API i wskazowki nawigacyjne.
     * Odpowiedz 200 potwierdza dzialajace poswiadczenia z rola {@code ECOM}.
     */
    public ServiceInfo info() {
        return transport.execute(ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH)
                .retryable(true)
                .build(), SERVICE_INFO);
    }

    /**
     * {@code GET /api/ecom/customers/{customerNumber}/points} — saldo punktow w rozbiciu
     * na oczekujace, dostepne i wygasle. Ten sam ksztalt, co odpowiednik kasowy.
     *
     * @throws pl.pietruszynski.loyaltyclub.sdk.core.exception.NotFoundException gdy klient nie istnieje
     */
    public PointsBalance getPointsBalance(String customerNumber) {
        return transport.execute(customerRequest(customerNumber, "points"), POINTS_BALANCE);
    }

    /**
     * {@code GET /api/ecom/customers/{customerNumber}/profile} — dane klienta wraz z progiem
     * lojalnosciowym i kodem polecajacym.
     *
     * @throws pl.pietruszynski.loyaltyclub.sdk.core.exception.NotFoundException gdy klient nie istnieje
     */
    public EcomCustomerProfile getCustomerProfile(String customerNumber) {
        return transport.execute(customerRequest(customerNumber, "profile"), PROFILE);
    }

    /**
     * {@code GET /api/ecom/customers/{customerNumber}/transactions} — historia punktowa klienta.
     */
    public List<CustomerTransaction> getTransactions(String customerNumber) {
        return transport.execute(customerRequest(customerNumber, "transactions"), TRANSACTIONS);
    }

    /**
     * {@code GET /api/ecom/customers/{customerNumber}/coupons} — kupony wydane klientowi,
     * niezaleznie od ich statusu.
     */
    public List<CustomerCoupon> getCoupons(String customerNumber) {
        return transport.execute(customerRequest(customerNumber, "coupons"), COUPONS);
    }

    private ApiRequest customerRequest(String customerNumber, String resource) {
        String normalized = Validate.requireText(customerNumber, "customerNumber");
        return ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH + "/customers/" + Uris.encodePathSegment(normalized) + "/" + resource)
                .retryable(true)
                .build();
    }

    /** Builder klienta e-commerce. */
    public static final class Builder extends EcomClientBuilderSupport<Builder> {

        private Builder() {
        }

        public EcomClient build() {
            return new EcomClient(buildTransport(requireAuthentication()));
        }
    }
}
