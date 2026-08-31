package pl.pietruszynski.loyaltyclub.sdk.ecom;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.pietruszynski.loyaltyclub.sdk.core.AbstractApiClient;
import pl.pietruszynski.loyaltyclub.sdk.core.http.ApiRequest;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpMethod;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PageRequests;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PageResponse;
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
 *         .credentials("ecom-shop", "haslo")
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
    private static final TypeReference<PageResponse<CustomerTransaction>> TRANSACTIONS_PAGE = new TypeReference<>() {
    };
    private static final TypeReference<PageResponse<CustomerCoupon>> COUPONS_PAGE = new TypeReference<>() {
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
     * Klient {@code /api/coupon/**} korzystajacy z tej samej sesji i tej samej puli polaczen.
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
     * lojalnosciowym, dorobkiem punktowym, kodem polecajacym i stanem konta.
     *
     * @throws pl.pietruszynski.loyaltyclub.sdk.core.exception.NotFoundException gdy klient nie istnieje
     */
    public EcomCustomerProfile getCustomerProfile(String customerNumber) {
        return transport.execute(customerRequest(customerNumber, "profile"), PROFILE);
    }

    /**
     * {@code GET /api/ecom/customers/{customerNumber}/transactions} — cala historia punktowa
     * klienta, rosnaco po dacie.
     *
     * <p>Dla kont z dluga historia siegnij po {@link #getTransactionsPage(String, Integer, Integer)}:
     * ten wariant sciaga wszystko jednym zadaniem.
     */
    public List<CustomerTransaction> getTransactions(String customerNumber) {
        return transport.execute(customerRequest(customerNumber, "transactions"), TRANSACTIONS);
    }

    /**
     * {@code GET /api/ecom/customers/{customerNumber}/transactions/paged} — historia punktowa
     * strona po stronie, malejaco po dacie (najnowsze pierwsze).
     *
     * @param page numer strony liczony od zera; {@code null} oznacza pierwsza
     * @param size rozmiar strony, najwyzej {@value PageRequests#MAX_PAGE_SIZE};
     *             {@code null} zostawia backendowi wartosc domyslna ({@value PageRequests#DEFAULT_PAGE_SIZE})
     */
    public PageResponse<CustomerTransaction> getTransactionsPage(String customerNumber, Integer page, Integer size) {
        return transport.execute(pagedRequest(customerNumber, "transactions/paged", page, size), TRANSACTIONS_PAGE);
    }

    /**
     * {@code GET /api/ecom/customers/{customerNumber}/coupons} — kupony wydane klientowi,
     * niezaleznie od ich statusu.
     */
    public List<CustomerCoupon> getCoupons(String customerNumber) {
        return transport.execute(customerRequest(customerNumber, "coupons"), COUPONS);
    }

    /**
     * {@code GET /api/ecom/customers/{customerNumber}/coupons/paged} — kupony klienta strona
     * po stronie, malejaco po dacie wydania.
     *
     * @param page numer strony liczony od zera; {@code null} oznacza pierwsza
     * @param size rozmiar strony, najwyzej {@value PageRequests#MAX_PAGE_SIZE};
     *             {@code null} zostawia backendowi wartosc domyslna ({@value PageRequests#DEFAULT_PAGE_SIZE})
     */
    public PageResponse<CustomerCoupon> getCouponsPage(String customerNumber, Integer page, Integer size) {
        return transport.execute(pagedRequest(customerNumber, "coupons/paged", page, size), COUPONS_PAGE);
    }

    private ApiRequest customerRequest(String customerNumber, String resource) {
        return ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(customerPath(customerNumber, resource))
                .retryable(true)
                .build();
    }

    private ApiRequest pagedRequest(String customerNumber, String resource, Integer page, Integer size) {
        var builder = ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(customerPath(customerNumber, resource))
                .retryable(true);

        // Parametr pominiety zostawia backendowi jego wartosc domyslna, wiec nie wysylamy go pusto.
        if (PageRequests.validatePage(page) != null) {
            builder.queryParam("page", page.toString());
        }
        if (PageRequests.validateSize(size) != null) {
            builder.queryParam("size", size.toString());
        }
        return builder.build();
    }

    private String customerPath(String customerNumber, String resource) {
        String normalized = Validate.requireText(customerNumber, "customerNumber");
        return BASE_PATH + "/customers/" + Uris.encodePathSegment(normalized) + "/" + resource;
    }

    /** Builder klienta e-commerce. */
    public static final class Builder extends EcomClientBuilderSupport<Builder> {

        private Builder() {
        }

        public EcomClient build() {
            requireCredentials();
            return new EcomClient(buildEcomTransport());
        }
    }
}
