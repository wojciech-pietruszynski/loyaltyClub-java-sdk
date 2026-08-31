package pl.pietruszynski.loyaltyclub.sdk.store;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.pietruszynski.loyaltyclub.sdk.core.AbstractApiClient;
import pl.pietruszynski.loyaltyclub.sdk.core.AbstractClientBuilder;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.AuthenticationProvider;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.BasicAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.BearerTokenAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubValidationException;
import pl.pietruszynski.loyaltyclub.sdk.core.http.ApiRequest;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpMethod;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PointsBalance;
import pl.pietruszynski.loyaltyclub.sdk.core.model.ServiceInfo;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Uris;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;
import pl.pietruszynski.loyaltyclub.sdk.store.auth.StoreJwtAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreReturnRequest;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreSaleRequest;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreTransactionResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Klient API kasowego {@code /api/store/**} — rejestracja sprzedazy i zwrotow oraz
 * odczyt salda punktow klienta. Wymaga konta z rola {@code STORE}.
 *
 * <p>Typowe uzycie z automatycznym logowaniem i odswiezaniem tokenu JWT:
 * <pre>{@code
 * try (StoreClient store = StoreClient.builder()
 *         .baseUrl("http://localhost:8089")
 *         .credentials("kasa-01", "haslo")
 *         .defaultCountryCode("PL")
 *         .build()) {
 *
 *     StoreTransactionResponse sale = store.registerSale(StoreSaleRequest.builder()
 *             .customerNumber("CUST-000123")
 *             .sourceTransactionNumber("POS-2026-08-28-0001")
 *             .totalAmount(new BigDecimal("59.98"))
 *             .item(StoreTransactionItem.builder()
 *                     .ean("5901234123457")
 *                     .name("Kawa ziarnista 1 kg")
 *                     .hierarchy(Hierarchy.builder().hierarchy("FOOD").build())
 *                     .price(ItemPrice.builder().amount(new BigDecimal("59.98")).currency("PLN").build())
 *                     .build())
 *             .build());
 * }
 * }</pre>
 *
 * <p>Instancja jest bezpieczna watkowo — tworz ja raz na aplikacje i wspoldziel.
 */
public class StoreClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/store";
    private static final String COUNTRY_CODE_HEADER = "X-CountryCode";

    private static final TypeReference<ServiceInfo> SERVICE_INFO = new TypeReference<>() {
    };
    private static final TypeReference<PointsBalance> POINTS_BALANCE = new TypeReference<>() {
    };
    private static final TypeReference<StoreTransactionResponse> TRANSACTION = new TypeReference<>() {
    };

    private final String defaultCountryCode;

    StoreClient(HttpTransport transport, String defaultCountryCode) {
        super(transport);
        this.defaultCountryCode = defaultCountryCode;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code GET /api/store} — metadane integracji. Odpowiedz 200 potwierdza, ze poswiadczenia
     * dzialaja i konto ma role {@code STORE}, wiec nadaje sie na health-check przy starcie kasy.
     */
    public ServiceInfo info() {
        return transport.execute(ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH)
                .retryable(true)
                .build(), SERVICE_INFO);
    }

    /**
     * {@code POST /api/store/transactions/sale} — rejestruje sprzedaz i nalicza punkty,
     * uzywajac domyslnego kodu kraju ustawionego w builderze.
     */
    public StoreTransactionResponse registerSale(StoreSaleRequest request) {
        return registerSale(requireDefaultCountryCode(), request);
    }

    /**
     * {@code POST /api/store/transactions/sale} — rejestruje sprzedaz i nalicza punkty.
     *
     * <p>Operacja nie jest ponawiana automatycznie: przy bledzie sieci nie da sie odroznic
     * zadania nieodebranego od zapisanego. Po {@link pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubTransportException}
     * ponow z tym samym {@code sourceTransactionNumber} — backend wymusza jego unikalnosc,
     * wiec duplikat skonczy sie HTTP 400 zamiast podwojnym naliczeniem.
     *
     * @param countryCode kod kraju sklepu, trafia do naglowka {@code X-CountryCode}
     */
    public StoreTransactionResponse registerSale(String countryCode, StoreSaleRequest request) {
        String normalizedCountryCode = StoreRequestValidator.normalizeCountryCode(countryCode);
        StoreRequestValidator.validateSale(request);

        return transport.execute(ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH + "/transactions/sale")
                .header(COUNTRY_CODE_HEADER, normalizedCountryCode)
                .body(request)
                .retryable(false)
                .build(), TRANSACTION);
    }

    /**
     * {@code POST /api/store/transactions/return} — rejestruje zwrot, uzywajac domyslnego
     * kodu kraju ustawionego w builderze.
     */
    public StoreTransactionResponse registerReturn(StoreReturnRequest request) {
        return registerReturn(requireDefaultCountryCode(), request);
    }

    /**
     * {@code POST /api/store/transactions/return} — wycofuje punkty naliczone wskazana sprzedaza.
     * Tak jak sprzedaz, nie jest ponawiana automatycznie.
     *
     * @param countryCode kod kraju sklepu; musi zgadzac sie z krajem pierwotnej sprzedazy
     */
    public StoreTransactionResponse registerReturn(String countryCode, StoreReturnRequest request) {
        String normalizedCountryCode = StoreRequestValidator.normalizeCountryCode(countryCode);
        StoreRequestValidator.validateReturn(request);

        return transport.execute(ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH + "/transactions/return")
                .header(COUNTRY_CODE_HEADER, normalizedCountryCode)
                .body(request)
                .retryable(false)
                .build(), TRANSACTION);
    }

    /**
     * {@code GET /api/store/customers/{customerNumber}/points} — saldo punktow klienta
     * w rozbiciu na oczekujace, dostepne i wygasle.
     *
     * @throws pl.pietruszynski.loyaltyclub.sdk.core.exception.NotFoundException gdy klient nie istnieje
     */
    public PointsBalance getPointsBalance(String customerNumber) {
        String normalized = Validate.requireText(customerNumber, "customerNumber");
        return transport.execute(ApiRequest.builder()
                .method(HttpMethod.GET)
                .path(BASE_PATH + "/customers/" + Uris.encodePathSegment(normalized) + "/points")
                .retryable(true)
                .build(), POINTS_BALANCE);
    }

    private String requireDefaultCountryCode() {
        if (defaultCountryCode == null) {
            throw new LoyaltyClubValidationException(
                    "Nie ustawiono defaultCountryCode — podaj kod kraju w wywolaniu albo w builderze klienta");
        }
        return defaultCountryCode;
    }

    /** Builder klienta sklepowego. */
    public static final class Builder extends AbstractClientBuilder<Builder> {

        private String username;
        private String password;
        private Duration tokenRefreshSkew;
        private AuthenticationProvider authentication;
        private String defaultCountryCode;

        private Builder() {
        }

        /**
         * Poswiadczenia uzytkownika sklepu. SDK samo zaloguje sie przez
         * {@code POST /api/store/auth/login} i bedzie przedluzac sesje przez
         * {@code POST /api/store/auth/refresh}, zanim token wygasnie — kasa nie musi
         * przechowywac hasla dluzej niz do pierwszego logowania.
         */
        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        /**
         * Zapas czasu, z jakim token jest wymieniany przed wygasnieciem; domyslnie 60 s.
         * Token backendu zyje 15 minut.
         */
        public Builder tokenRefreshSkew(Duration tokenRefreshSkew) {
            this.tokenRefreshSkew = tokenRefreshSkew;
            return this;
        }

        /**
         * HTTP Basic zamiast JWT. Backend akceptuje oba warianty dla {@code /api/store/**},
         * ale przy Basic haslo leci w kazdym zadaniu.
         */
        public Builder basicAuth(String username, String password) {
            this.authentication = new BasicAuthentication(username, password);
            return this;
        }

        /** Gotowy token JWT zdobyty poza SDK. */
        public Builder bearerToken(String token) {
            this.authentication = new BearerTokenAuthentication(token);
            return this;
        }

        /** Wlasna implementacja zrodla poswiadczen. */
        public Builder authentication(AuthenticationProvider authentication) {
            this.authentication = authentication;
            return this;
        }

        /**
         * Kod kraju uzywany przez warianty {@code registerSale}/{@code registerReturn}
         * bez jawnego parametru. Kasa stoi w jednym kraju, wiec zwykle ustawia sie go raz.
         */
        public Builder defaultCountryCode(String defaultCountryCode) {
            this.defaultCountryCode = defaultCountryCode;
            return this;
        }

        public StoreClient build() {
            String normalizedCountryCode = defaultCountryCode == null
                    ? null
                    : StoreRequestValidator.normalizeCountryCode(defaultCountryCode);

            if (authentication == null) {
                Validate.requireText(username, "username");
                Validate.requireNonNull(password, "password");

                // Logowanie musi isc tym samym transportem, ale bez naglowka Authorization.
                // Transport powstaje dopiero po zbudowaniu uwierzytelnienia, stad referencja z opoznieniem.
                AtomicReference<HttpTransport> loginTransport = new AtomicReference<>();
                StoreJwtAuthentication jwtAuthentication =
                        new StoreJwtAuthentication(loginTransport::get, username, password, tokenRefreshSkew);

                HttpTransport transport = buildTransport(jwtAuthentication);
                loginTransport.set(transport.withoutAuthentication());
                return new StoreClient(transport, normalizedCountryCode);
            }

            return new StoreClient(buildTransport(authentication), normalizedCountryCode);
        }
    }
}
