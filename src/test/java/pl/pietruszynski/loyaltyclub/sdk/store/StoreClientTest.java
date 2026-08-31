package pl.pietruszynski.loyaltyclub.sdk.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.pietruszynski.loyaltyclub.sdk.core.MockApiServer;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.BadRequestException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubValidationException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.NotFoundException;
import pl.pietruszynski.loyaltyclub.sdk.core.json.LoyaltyClubJson;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PointsBalance;
import pl.pietruszynski.loyaltyclub.sdk.core.retry.RetryPolicy;
import pl.pietruszynski.loyaltyclub.sdk.store.model.Hierarchy;
import pl.pietruszynski.loyaltyclub.sdk.store.model.ItemPrice;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreReturnRequest;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreSaleRequest;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreTransactionItem;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreTransactionResponse;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionState;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreClientTest {

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

    private StoreClient clientWithJwtLogin() {
        return StoreClient.builder()
                .baseUrl(server.baseUrl())
                .credentials("kasa-01", "haslo")
                .defaultCountryCode("pl")
                .retryPolicy(RetryPolicy.none())
                .requestTimeout(Duration.ofSeconds(5))
                .build();
    }

    private void enqueueLogin() {
        long expiresAt = Instant.now().plusSeconds(900).toEpochMilli();
        server.enqueueJson(200, """
                {"token":"jwt-store-token","expiresAt":%d,"role":"STORE","country":null}"""
                .formatted(expiresAt));
    }

    private static StoreSaleRequest sampleSale() {
        return StoreSaleRequest.builder()
                .customerNumber("CUST-000123")
                .sourceTransactionNumber("POS-2026-0001")
                .totalAmount(new BigDecimal("59.98"))
                .purchaseTimestamp(LocalDateTime.of(2026, 8, 28, 12, 0))
                .item(StoreTransactionItem.builder()
                        .cartPosition("1")
                        .ean("5901234123457")
                        .name("Kawa ziarnista 1 kg")
                        .hierarchy(Hierarchy.builder().hierarchy("FOOD").productClass("COFFEE").build())
                        .price(ItemPrice.builder().amount(new BigDecimal("59.98")).currency("PLN").build())
                        .build())
                .build();
    }

    @Test
    @DisplayName("loguje sie raz i uzywa tokenu Bearer w kolejnych wywolaniach")
    void logsInOnceAndReusesToken() {
        enqueueLogin();
        server.enqueueJson(200, """
                {"customerNumber":"CUST-000123","pendingPoints":10,"availablePoints":50,"expiredPoints":1}""");
        server.enqueueJson(200, """
                {"customerNumber":"CUST-000123","pendingPoints":10,"availablePoints":50,"expiredPoints":1}""");

        try (StoreClient client = clientWithJwtLogin()) {
            client.getPointsBalance("CUST-000123");
            PointsBalance balance = client.getPointsBalance("CUST-000123");
            assertEquals(50, balance.getAvailablePoints());
        }

        MockApiServer.RecordedRequest login = server.takeRequest();
        assertEquals("POST", login.method());
        assertEquals("/api/store/auth/login", login.path());
        assertTrue(login.body().contains("\"username\":\"kasa-01\""));

        assertEquals("Bearer jwt-store-token", server.takeRequest().header("Authorization"));
        assertEquals("Bearer jwt-store-token", server.takeRequest().header("Authorization"));
        // Trzy zadania w sumie: jedno logowanie i dwa odczyty salda — token nie byl pobierany ponownie.
        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("po HTTP 401 loguje sie ponownie i powtarza wywolanie")
    void reLoginsAfterUnauthorized() {
        enqueueLogin();
        server.enqueueEmpty(401);
        long expiresAt = Instant.now().plusSeconds(900).toEpochMilli();
        server.enqueueJson(200, """
                {"token":"jwt-swiezy","expiresAt":%d,"role":"STORE"}""".formatted(expiresAt));
        server.enqueueJson(200, """
                {"customerNumber":"CUST-000123","availablePoints":7}""");

        try (StoreClient client = clientWithJwtLogin()) {
            assertEquals(7, client.getPointsBalance("CUST-000123").getAvailablePoints());
        }

        server.takeRequest();
        assertEquals("Bearer jwt-store-token", server.takeRequest().header("Authorization"));
        assertEquals("/api/store/auth/login", server.takeRequest().path());
        assertEquals("Bearer jwt-swiezy", server.takeRequest().header("Authorization"));
    }

    @Test
    @DisplayName("rejestracja sprzedazy wysyla naglowek X-CountryCode i cialo zgodne z kontraktem backendu")
    void registerSaleSendsCountryHeaderAndBody() throws Exception {
        enqueueLogin();
        server.enqueueJson(200, """
                {"transactionId":1001,"customerId":7,"customerNumber":"CUST-000123","type":"SALE",
                 "state":"PENDING","points":59,"amount":59.98,"pointsPerCurrency":1.00,
                 "purchaseTimestamp":"2026-08-28T12:00:00","availableFrom":"2026-09-11T12:00:00",
                 "expiresAt":"2027-08-28T12:00:00"}""");

        try (StoreClient client = clientWithJwtLogin()) {
            StoreTransactionResponse response = client.registerSale(sampleSale());

            assertEquals(1001L, response.getTransactionId());
            assertEquals(TransactionType.SALE, response.getType());
            assertEquals(TransactionState.PENDING, response.getState());
            assertEquals(59, response.getPoints());
            assertEquals(LocalDateTime.of(2026, 9, 11, 12, 0), response.getAvailableFrom());
        }

        server.takeRequest();
        MockApiServer.RecordedRequest sale = server.takeRequest();
        assertEquals("POST", sale.method());
        assertEquals("/api/store/transactions/sale", sale.path());
        // Builder dostal "pl" — backend oczekuje wielkich liter, wiec SDK normalizuje kod kraju.
        assertEquals("PL", sale.header("X-CountryCode"));
        assertEquals("application/json", sale.header("Content-Type"));

        JsonNode body = MAPPER.readTree(sale.body());
        assertEquals("CUST-000123", body.get("customerNumber").asText());
        assertEquals("POS-2026-0001", body.get("sourceTransactionNumber").asText());
        assertEquals("59.98", body.get("totalAmount").asText());
        assertEquals("2026-08-28T12:00:00", body.get("purchaseTimestamp").asText());
        assertEquals("5901234123457", body.get("items").get(0).get("ean").asText());
        assertEquals("FOOD", body.get("items").get(0).get("hierarchy").get("hierarchy").asText());
        assertEquals("PLN", body.get("items").get(0).get("price").get("currency").asText());
    }

    @Test
    @DisplayName("rejestracja zwrotu wysyla numer transakcji sprzedazy")
    void registerReturnSendsSaleTransactionNumber() throws Exception {
        enqueueLogin();
        server.enqueueJson(200, """
                {"transactionId":1002,"type":"RETURN","state":"AVAILABLE","points":-59}""");

        try (StoreClient client = clientWithJwtLogin()) {
            StoreTransactionResponse response = client.registerReturn("DE", StoreReturnRequest.builder()
                    .customerNumber("CUST-000123")
                    .sourceTransactionNumber("POS-2026-0002")
                    .saleTransactionNumber("POS-2026-0001")
                    .totalAmount(new BigDecimal("59.98"))
                    .item(StoreTransactionItem.builder()
                            .ean("5901234123457")
                            .name("Kawa ziarnista 1 kg")
                            .hierarchy(Hierarchy.builder().hierarchy("FOOD").build())
                            .price(ItemPrice.builder().amount(new BigDecimal("59.98")).currency("PLN").build())
                            .build())
                    .build());

            assertEquals(TransactionType.RETURN, response.getType());
            assertEquals(-59, response.getPoints());
        }

        server.takeRequest();
        MockApiServer.RecordedRequest returnRequest = server.takeRequest();
        assertEquals("/api/store/transactions/return", returnRequest.path());
        assertEquals("DE", returnRequest.header("X-CountryCode"));
        assertEquals("POS-2026-0001",
                MAPPER.readTree(returnRequest.body()).get("saleTransactionNumber").asText());
    }

    @Test
    @DisplayName("nieznana wartosc enuma z nowszego backendu nie wysadza deserializacji")
    void unknownEnumValueFallsBackToUnknown() {
        enqueueLogin();
        server.enqueueJson(200, """
                {"transactionId":1003,"type":"LOYALTY_BONUS","state":"FROZEN"}""");

        try (StoreClient client = clientWithJwtLogin()) {
            StoreTransactionResponse response = client.registerSale(sampleSale());

            assertEquals(TransactionType.UNKNOWN, response.getType());
            assertEquals(TransactionState.UNKNOWN, response.getState());
        }
    }

    @Test
    @DisplayName("odrzuca lokalnie paragon, w ktorym kwota nie zgadza sie z suma pozycji")
    void rejectsTotalAmountMismatchLocally() {
        try (StoreClient client = clientWithJwtLogin()) {
            StoreSaleRequest request = StoreSaleRequest.builder()
                    .customerNumber("CUST-000123")
                    .sourceTransactionNumber("POS-2026-0003")
                    .totalAmount(new BigDecimal("100.00"))
                    .item(StoreTransactionItem.builder()
                            .ean("5901234123457")
                            .name("Kawa")
                            .hierarchy(Hierarchy.builder().hierarchy("FOOD").build())
                            .price(ItemPrice.builder().amount(new BigDecimal("59.98")).currency("PLN").build())
                            .build())
                    .build();

            LoyaltyClubValidationException exception =
                    assertThrows(LoyaltyClubValidationException.class, () -> client.registerSale(request));
            assertTrue(exception.getMessage().contains("totalAmount"));
        }

        // Zadne zadanie nie poszlo na serwer — nawet logowanie.
        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("odrzuca lokalnie paragon bez pozycji i bez numeru transakcji zrodlowej")
    void rejectsIncompleteRequestsLocally() {
        try (StoreClient client = clientWithJwtLogin()) {
            assertThrows(LoyaltyClubValidationException.class, () -> client.registerSale(
                    StoreSaleRequest.builder()
                            .customerNumber("CUST-1")
                            .sourceTransactionNumber("POS-1")
                            .totalAmount(new BigDecimal("10.00"))
                            .build()));

            assertThrows(LoyaltyClubValidationException.class, () -> client.registerSale(
                    StoreSaleRequest.builder()
                            .customerNumber("CUST-1")
                            .totalAmount(new BigDecimal("59.98"))
                            .item(StoreTransactionItem.builder()
                                    .ean("5901234123457")
                                    .name("Kawa")
                                    .hierarchy(Hierarchy.builder().hierarchy("FOOD").build())
                                    .price(ItemPrice.builder().amount(new BigDecimal("59.98")).currency("PLN").build())
                                    .build())
                            .build()));
        }

        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("odrzuca kod kraju dluzszy niz trzy znaki, tak jak zrobilby to backend")
    void rejectsTooLongCountryCode() {
        try (StoreClient client = clientWithJwtLogin()) {
            assertThrows(LoyaltyClubValidationException.class,
                    () -> client.registerSale("POLSKA", sampleSale()));
        }
    }

    @Test
    @DisplayName("bez domyslnego kodu kraju wariant jednoargumentowy zglasza czytelny blad")
    void requiresCountryCodeWhenDefaultMissing() {
        try (StoreClient client = StoreClient.builder()
                .baseUrl(server.baseUrl())
                .basicAuth("kasa-01", "haslo")
                .build()) {

            LoyaltyClubValidationException exception =
                    assertThrows(LoyaltyClubValidationException.class, () -> client.registerSale(sampleSale()));
            assertTrue(exception.getMessage().contains("defaultCountryCode"));
        }
    }

    @Test
    @DisplayName("wariant Basic nie wywoluje logowania i wysyla naglowek Authorization")
    void basicAuthSkipsLogin() {
        server.enqueueJson(200, """
                {"customerNumber":"CUST-1","availablePoints":3}""");

        try (StoreClient client = StoreClient.builder()
                .baseUrl(server.baseUrl())
                .basicAuth("kasa-01", "haslo")
                .build()) {
            assertEquals(3, client.getPointsBalance("CUST-1").getAvailablePoints());
        }

        MockApiServer.RecordedRequest request = server.takeRequest();
        assertEquals("/api/store/customers/CUST-1/points", request.path());
        assertTrue(request.header("Authorization").startsWith("Basic "));
    }

    @Test
    @DisplayName("nieznany klient konczy sie NotFoundException z komunikatem backendu")
    void mapsUnknownCustomerToNotFound() {
        enqueueLogin();
        server.enqueueProblem(404, """
                {"status":404,"detail":"Customer not found for customerNumber: CUST-999"}""");

        try (StoreClient client = clientWithJwtLogin()) {
            NotFoundException exception =
                    assertThrows(NotFoundException.class, () -> client.getPointsBalance("CUST-999"));
            assertTrue(exception.getDetail().orElseThrow().contains("CUST-999"));
        }
    }

    @Test
    @DisplayName("duplikat numeru transakcji zrodlowej wraca jako BadRequestException")
    void mapsDuplicateSourceTransactionNumber() {
        enqueueLogin();
        server.enqueueProblem(400, """
                {"status":400,"detail":"sourceTransactionNumber must be unique"}""");

        try (StoreClient client = clientWithJwtLogin()) {
            BadRequestException exception =
                    assertThrows(BadRequestException.class, () -> client.registerSale(sampleSale()));
            assertEquals("sourceTransactionNumber must be unique", exception.getDetail().orElseThrow());
        }
    }

    @Test
    @DisplayName("info() czyta metadane integracji")
    void readsServiceInfo() {
        enqueueLogin();
        server.enqueueJson(200, """
                {"name":"store","status":"ready"}""");

        try (StoreClient client = clientWithJwtLogin()) {
            assertEquals("store", client.info().getName());
        }
    }
}
