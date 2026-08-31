package pl.pietruszynski.loyaltyclub.sdk.ecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.pietruszynski.loyaltyclub.sdk.core.MockApiServer;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.ForbiddenException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubValidationException;
import pl.pietruszynski.loyaltyclub.sdk.core.json.LoyaltyClubJson;
import pl.pietruszynski.loyaltyclub.sdk.core.model.CustomerStatus;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PageRequests;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PageResponse;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PointsBalance;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionState;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionType;
import pl.pietruszynski.loyaltyclub.sdk.core.retry.RetryPolicy;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponReason;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponRedeemRequest;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponStatus;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponRedeemResponse;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponValidationResponse;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponValidationStatus;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CustomerTransaction;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.EcomCustomerProfile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcomClientTest {

    private static final ObjectMapper MAPPER = LoyaltyClubJson.createDefault();

    private MockApiServer server;

    @BeforeEach
    void startServer() {
        server = MockApiServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private EcomClient client() {
        return EcomClient.builder()
                .baseUrl(server.baseUrl())
                .basicAuth("ecom-shop", "haslo")
                .retryPolicy(RetryPolicy.none())
                .requestTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Test
    @DisplayName("czyta profil lojalnosciowy klienta")
    void readsCustomerProfile() {
        server.enqueueJson(200, """
                {"customerId":7,"customerNumber":"CUST-000123","firstName":"Anna","lastName":"Kowalska",
                 "email":"anna@example.com","phoneNumber":"+48123456789","country":"PL",
                 "loyaltyPoints":250,"lifetimePoints":1450,"loyaltyTierCode":"SILVER",
                 "referralCode":"REF-ANNA","status":"ACTIVE"}""");

        try (EcomClient client = client()) {
            EcomCustomerProfile profile = client.getCustomerProfile("CUST-000123");

            assertEquals("Anna", profile.getFirstName());
            assertEquals("SILVER", profile.getLoyaltyTierCode());
            assertEquals(250, profile.getLoyaltyPoints());
            // Dorobek punktowy nie spada przy wymianie punktow, wiec jest wyzszy od salda.
            assertEquals(1450, profile.getLifetimePoints());
            assertEquals(CustomerStatus.ACTIVE, profile.getStatus());
            assertTrue(profile.getStatus().allowsPointOperations());
        }

        MockApiServer.RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.method());
        assertEquals("/api/ecom/customers/CUST-000123/profile", request.path());
        assertEquals("Basic ZWNvbS1zaG9wOmhhc2xv", request.header("Authorization"));
    }

    @Test
    @DisplayName("czyta saldo punktow w tym samym ksztalcie, co API kasowe")
    void readsPointsBalance() {
        server.enqueueJson(200, """
                {"customerId":7,"customerNumber":"CUST-000123","pendingPoints":40,
                 "availablePoints":210,"expiredPoints":5}""");

        try (EcomClient client = client()) {
            PointsBalance balance = client.getPointsBalance("CUST-000123");

            assertEquals(40, balance.getPendingPoints());
            assertEquals(210, balance.getAvailablePoints());
            assertEquals(5, balance.getExpiredPoints());
        }

        assertEquals("/api/ecom/customers/CUST-000123/points", server.takeRequest().path());
    }

    @Test
    @DisplayName("czyta historie punktowa jako liste")
    void readsTransactions() {
        server.enqueueJson(200, """
                [{"id":1,"points":59,"description":"Zakup POS-2026-0001","timestamp":"2026-08-28T12:00:00",
                  "availableFrom":"2026-09-11T12:00:00","expiresAt":"2027-08-28T12:00:00",
                  "type":"SALE","state":"PENDING","amount":59.98},
                 {"id":2,"points":-59,"description":"Zwrot POS-2026-0002","timestamp":"2026-08-29T09:30:00",
                  "type":"RETURN","state":"PENDING","amount":59.98}]""");

        try (EcomClient client = client()) {
            List<CustomerTransaction> transactions = client.getTransactions("CUST-000123");

            assertEquals(2, transactions.size());
            assertEquals(59, transactions.getFirst().getPoints());
            assertEquals(LocalDateTime.of(2026, 9, 11, 12, 0), transactions.getFirst().getAvailableFrom());
            assertEquals(-59, transactions.get(1).getPoints());
            assertNull(transactions.get(1).getAvailableFrom());

            // Rodzaj i stan przychodza z backendu, wiec integracja nie musi ich zgadywac z opisu.
            assertEquals(TransactionType.SALE, transactions.getFirst().getType());
            assertEquals(TransactionState.PENDING, transactions.getFirst().getState());
            assertEquals(0, new BigDecimal("59.98").compareTo(transactions.getFirst().getAmount()));
            assertEquals(LocalDateTime.of(2027, 8, 28, 12, 0), transactions.getFirst().getExpiresAt());
            assertEquals(TransactionType.RETURN, transactions.get(1).getType());
        }

        assertEquals("/api/ecom/customers/CUST-000123/transactions", server.takeRequest().path());
    }

    @Test
    @DisplayName("czyta kupony klienta")
    void readsCoupons() {
        server.enqueueJson(200, """
                [{"id":11,"couponCode":"PL-ABC123","customerId":7,"couponValue":20.00,
                  "minimumPurchaseValue":100.00,"requiredPoints":200,"validityDays":30,
                  "couponPrefix":"PL","status":"ACTIVE","issuedAt":"2026-08-28T12:00:00",
                  "expiresAt":"2026-09-27T12:00:00"}]""");

        try (EcomClient client = client()) {
            List<CustomerCoupon> coupons = client.getCoupons("CUST-000123");

            assertEquals(1, coupons.size());
            CustomerCoupon coupon = coupons.getFirst();
            assertEquals("PL-ABC123", coupon.getCouponCode());
            assertEquals(0, new BigDecimal("20.00").compareTo(coupon.getCouponValue()));
            assertEquals(CouponStatus.ACTIVE, coupon.getStatus());
        }
    }

    @Test
    @DisplayName("koduje numer klienta w sciezce")
    void encodesCustomerNumberInPath() {
        server.enqueueJson(200, "{}");

        try (EcomClient client = client()) {
            client.getPointsBalance("CUST/000 123");
        }

        assertEquals("/api/ecom/customers/CUST%2F000%20123/points", server.takeRequest().path());
    }

    @Test
    @DisplayName("pusty numer klienta jest odrzucany lokalnie, bez wywolania sieciowego")
    void rejectsBlankCustomerNumber() {
        try (EcomClient client = client()) {
            assertThrows(LoyaltyClubValidationException.class, () -> client.getCustomerProfile("  "));
        }

        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("konto bez roli ECOM konczy sie ForbiddenException")
    void mapsWrongRoleToForbidden() {
        server.enqueueProblem(403, """
                {"status":403,"detail":"Forbidden"}""");

        try (EcomClient client = client()) {
            assertThrows(ForbiddenException.class, () -> client.getCustomerProfile("CUST-1"));
        }
    }

    @Test
    @DisplayName("klient kuponowy wspoldzieli poswiadczenia i wysyla naglowek Idempotency-Key")
    void couponClientSharesCredentialsAndSendsIdempotencyKey() throws Exception {
        server.enqueueJson(200, """
                {"couponCode":"PL-ABC123","customerNumber":"CUST-000123","status":"ACTIVE",
                 "issuedAt":"2026-08-28T12:00:00","expiresAt":"2026-09-27T12:00:00",
                 "definition":{"couponTemplateId":3,"couponValue":20.00,"minimumPurchaseValue":100.00,
                               "requiredPoints":200,"validityDays":30,"couponPrefix":"PL","country":"PL"}}""");

        try (EcomClient client = client()) {
            CouponRedeemResponse response = client.coupons().redeemPoints("order-2026-0001",
                    CouponRedeemRequest.builder()
                            .customerNumber("CUST-000123")
                            .couponTemplateId(3L)
                            .build());

            assertEquals("PL-ABC123", response.getCouponCode());
            assertEquals(200, response.getDefinition().getRequiredPoints());
        }

        MockApiServer.RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.method());
        assertEquals("/api/coupon/redeem-points", request.path());
        assertEquals("order-2026-0001", request.header("Idempotency-Key"));
        assertEquals("Basic ZWNvbS1zaG9wOmhhc2xv", request.header("Authorization"));

        JsonNode body = MAPPER.readTree(request.body());
        assertEquals("CUST-000123", body.get("customerNumber").asText());
        assertEquals(3, body.get("couponTemplateId").asInt());
    }

    @Test
    @DisplayName("brak klucza idempotentnosci jest wychwytywany lokalnie")
    void requiresIdempotencyKey() {
        try (EcomClient client = client()) {
            CouponRedeemRequest request = CouponRedeemRequest.builder()
                    .customerNumber("CUST-000123")
                    .couponTemplateId(3L)
                    .build();

            assertThrows(LoyaltyClubValidationException.class, () -> client.coupons().redeemPoints("  ", request));
        }

        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("walidacja kuponu przekazuje parametry zapytania i zwraca werdykt")
    void validatesCoupon() {
        server.enqueueJson(200, """
                {"status":"VALID","couponCode":"PL-ABC123","customerNumber":"CUST-000123",
                 "couponStatus":"ACTIVE","issuedAt":"2026-08-28T12:00:00","expiresAt":"2026-09-27T12:00:00",
                 "definition":{"couponTemplateId":3,"couponValue":20.00,"minimumPurchaseValue":100.00}}""");

        try (EcomClient client = client()) {
            CouponValidationResponse validation = client.coupons().validate("PL-ABC123", "CUST-000123");

            assertTrue(validation.isValid());
            assertEquals(CouponValidationStatus.VALID, validation.getStatus());
            assertEquals(0, new BigDecimal("100.00").compareTo(validation.getDefinition().getMinimumPurchaseValue()));
        }

        MockApiServer.RecordedRequest request = server.takeRequest();
        assertEquals("/api/coupon/validate", request.path());
        assertEquals("couponCode=PL-ABC123&customerNumber=CUST-000123", request.query());
    }

    @Test
    @DisplayName("kupon odrzucony wraca jako status 200 z werdyktem, nie jako blad HTTP")
    void invalidCouponIsNotAnHttpError() {
        server.enqueueJson(200, """
                {"status":"COUPON_ALREADY_USED","couponCode":"PL-ABC123","customerNumber":"CUST-000123",
                 "couponStatus":"USED"}""");

        try (EcomClient client = client()) {
            CouponValidationResponse validation = client.coupons().validate("PL-ABC123", "CUST-000123");

            assertFalse(validation.isValid());
            assertEquals(CouponValidationStatus.COUPON_ALREADY_USED, validation.getStatus());
        }
    }

    @Test
    @DisplayName("nieznany werdykt z nowszego backendu jest traktowany jak odmowa")
    void unknownValidationStatusIsNotValid() {
        server.enqueueJson(200, """
                {"status":"COUPON_BLOCKED_BY_FRAUD_CHECK","couponCode":"PL-ABC123"}""");

        try (EcomClient client = client()) {
            CouponValidationResponse validation = client.coupons().validate("PL-ABC123", "CUST-000123");

            assertEquals(CouponValidationStatus.UNKNOWN, validation.getStatus());
            assertFalse(validation.isValid());
        }
    }

    @Test
    @DisplayName("samodzielny CouponClient dziala bez klienta e-commerce")
    void standaloneCouponClient() {
        server.enqueueJson(200, """
                {"status":"VALID","couponCode":"PL-ABC123"}""");

        try (CouponClient coupons = CouponClient.builder()
                .baseUrl(server.baseUrl())
                .basicAuth("ecom-shop", "haslo")
                .build()) {
            assertTrue(coupons.validate("PL-ABC123", "CUST-000123").isValid());
        }
    }

    @Test
    @DisplayName("builder bez poswiadczen zglasza czytelny blad")
    void builderRequiresCredentials() {
        LoyaltyClubValidationException exception = assertThrows(LoyaltyClubValidationException.class,
                () -> EcomClient.builder().baseUrl(server.baseUrl()).build());

        assertTrue(exception.getMessage().contains("basicAuth"));
    }

    @Test
    @DisplayName("czyta stronicowana historie punktowa i przekazuje parametry stronicowania")
    void readsTransactionsPage() {
        server.enqueueJson(200, """
                {"content":[{"id":9,"points":12,"type":"REFERRAL","state":"AVAILABLE",
                             "timestamp":"2026-08-30T10:00:00"}],
                 "page":1,"size":2,"totalElements":5,"totalPages":3,"first":false,"last":false}""");

        try (EcomClient client = client()) {
            PageResponse<CustomerTransaction> page = client.getTransactionsPage("CUST-000123", 1, 2);

            assertEquals(1, page.getContent().size());
            assertEquals(TransactionType.REFERRAL, page.getContent().getFirst().getType());
            assertEquals(5, page.getTotalElements());
            assertEquals(3, page.getTotalPages());
            assertTrue(page.hasNext());
            assertEquals(2, page.nextPage());
        }

        MockApiServer.RecordedRequest request = server.takeRequest();
        assertEquals("/api/ecom/customers/CUST-000123/transactions/paged", request.path());
        assertEquals("page=1&size=2", request.query());
    }

    @Test
    @DisplayName("czyta stronicowane kupony klienta")
    void readsCouponsPage() {
        server.enqueueJson(200, """
                {"content":[{"id":11,"couponCode":"PL-ABC123","status":"USED","reason":"COMPLAINT"}],
                 "page":0,"size":25,"totalElements":1,"totalPages":1,"first":true,"last":true}""");

        try (EcomClient client = client()) {
            PageResponse<CustomerCoupon> page = client.getCouponsPage("CUST-000123", null, null);

            assertEquals(CouponStatus.USED, page.getContent().getFirst().getStatus());
            assertTrue(page.getContent().getFirst().getStatus().isFinal());
            assertEquals(CouponReason.COMPLAINT, page.getContent().getFirst().getReason());
            assertFalse(page.hasNext());
            assertEquals(-1, page.nextPage());
        }

        MockApiServer.RecordedRequest request = server.takeRequest();
        assertEquals("/api/ecom/customers/CUST-000123/coupons/paged", request.path());
        // Brak parametrow zostawia backendowi jego wartosci domyslne.
        assertNull(request.query());
    }

    @Test
    @DisplayName("rozmiar strony ponad limit backendu jest odrzucany lokalnie")
    void rejectsPageSizeAboveBackendLimit() {
        try (EcomClient client = client()) {
            assertThrows(LoyaltyClubValidationException.class,
                    () -> client.getTransactionsPage("CUST-000123", 0, PageRequests.MAX_PAGE_SIZE + 1));
            assertThrows(LoyaltyClubValidationException.class,
                    () -> client.getCouponsPage("CUST-000123", -1, null));
        }

        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("werdykty dodane po stronie backendu sa rozpoznawane, nie mapowane na UNKNOWN")
    void recognizesNewValidationVerdicts() {
        server.enqueueJson(200, """
                {"status":"CUSTOMER_NOT_ACTIVE","couponCode":"PL-ABC123","couponStatus":"ACTIVE"}""");
        server.enqueueJson(200, """
                {"status":"COUPON_CANCELLED","couponCode":"PL-ABC123","couponStatus":"CANCELLED"}""");

        try (EcomClient client = client()) {
            CouponValidationResponse suspended = client.coupons().validate("PL-ABC123", "CUST-000123");
            assertEquals(CouponValidationStatus.CUSTOMER_NOT_ACTIVE, suspended.getStatus());
            assertFalse(suspended.isValid());

            CouponValidationResponse cancelled = client.coupons().validate("PL-ABC123", "CUST-000123");
            assertEquals(CouponValidationStatus.COUPON_CANCELLED, cancelled.getStatus());
            assertEquals(CouponStatus.CANCELLED, cancelled.getCouponStatus());
        }
    }

    @Test
    @DisplayName("zbyt dlugi klucz idempotentnosci jest odrzucany przed wywolaniem sieciowym")
    void rejectsTooLongIdempotencyKey() {
        try (EcomClient client = client()) {
            assertThrows(LoyaltyClubValidationException.class, () -> client.coupons().redeemPoints(
                    "k".repeat(CouponClient.MAX_IDEMPOTENCY_KEY_LENGTH + 1),
                    CouponRedeemRequest.builder().customerNumber("CUST-000123").couponTemplateId(3L).build()));
        }

        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("info() czyta wersje API z metadanych integracji")
    void readsServiceInfo() {
        server.enqueueJson(200, """
                {"name":"ecom","status":"ready","apiVersion":"1.0.0","docs":"Use GET /api/ecom/..."}""");

        try (EcomClient client = client()) {
            assertEquals("1.0.0", client.info().getApiVersion());
        }
    }
}
