package pl.pietruszynski.loyaltyclub.sdk.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.pietruszynski.loyaltyclub.sdk.core.json.LoyaltyClubJson;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PointsBalance;
import pl.pietruszynski.loyaltyclub.sdk.core.model.ProblemDetail;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoyaltyClubJsonTest {

    private final ObjectMapper mapper = LoyaltyClubJson.createDefault();

    /** Cialo zadania z {@code LocalDateTime}, odwzorowujace ksztalt zadan sklepowych. */
    record Payload(String customerNumber, LocalDateTime purchaseTimestamp) {
    }

    @Test
    @DisplayName("serializuje LocalDateTime jako ISO-8601 bez strefy, tak jak oczekuje backend")
    void serializesLocalDateTimeAsIsoString() throws Exception {
        String json = mapper.writeValueAsString(
                new Payload("CUST-1", LocalDateTime.of(2026, 8, 28, 21, 48, 5)));

        assertEquals("{\"customerNumber\":\"CUST-1\",\"purchaseTimestamp\":\"2026-08-28T21:48:05\"}", json);
    }

    @Test
    @DisplayName("pomija pola null, zeby backend zastosowal swoje wartosci domyslne")
    void omitsNullFields() throws Exception {
        String json = mapper.writeValueAsString(new Payload("CUST-1", null));

        assertEquals("{\"customerNumber\":\"CUST-1\"}", json);
    }

    @Test
    @DisplayName("ignoruje nieznane pola odpowiedzi, zeby nowsze API nie psulo starszego SDK")
    void ignoresUnknownResponseFields() throws Exception {
        PointsBalance balance = mapper.readValue("""
                {"customerNumber":"CUST-1","availablePoints":12,"nowePoleZPrzyszlosci":"x"}""", PointsBalance.class);

        assertEquals("CUST-1", balance.getCustomerNumber());
        assertEquals(12, balance.getAvailablePoints());
        assertNull(balance.getPendingPoints());
    }

    @Test
    @DisplayName("czyta ProblemDetail razem z niestandardowym polem errors")
    void readsProblemDetailWithCustomProperties() throws Exception {
        ProblemDetail problem = mapper.readValue("""
                {"type":"about:blank","title":"Bad Request","status":400,"detail":"Validation failed",
                 "errors":{"items":"Items are required","totalAmount":"Amount is required"}}""", ProblemDetail.class);

        assertEquals(400, problem.getStatus());
        assertEquals("Validation failed", problem.getDetail());
        assertEquals(Map.of("items", "Items are required", "totalAmount", "Amount is required"),
                problem.getFieldErrors());
    }

    @Test
    @DisplayName("ProblemDetail bez pola errors zwraca pusta mape zamiast null")
    void problemDetailWithoutErrorsReturnsEmptyMap() throws Exception {
        ProblemDetail problem = mapper.readValue("""
                {"status":404,"detail":"Customer not found"}""", ProblemDetail.class);

        assertTrue(problem.getFieldErrors().isEmpty());
        assertFalse(problem.getProperties().containsKey("errors"));
    }
}
