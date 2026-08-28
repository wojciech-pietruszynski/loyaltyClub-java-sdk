package pl.pietruszynski.loyaltyclub.sdk.core;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.AuthenticationProvider;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.BasicAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.BadRequestException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.ForbiddenException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.NotFoundException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.ServerException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.UnauthorizedException;
import pl.pietruszynski.loyaltyclub.sdk.core.http.ApiRequest;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpMethod;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PointsBalance;
import pl.pietruszynski.loyaltyclub.sdk.core.retry.RetryPolicy;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTransportTest {

    private static final TypeReference<PointsBalance> BALANCE = new TypeReference<>() {
    };

    private MockApiServer server;

    @BeforeEach
    void startServer() {
        server = MockApiServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private HttpTransport transport(RetryPolicy retryPolicy, AuthenticationProvider authentication) {
        return HttpTransport.builder()
                .baseUri(server.baseUrl())
                .retryPolicy(retryPolicy)
                .authentication(authentication)
                .requestTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static ApiRequest get(boolean retryable) {
        return ApiRequest.builder()
                .method(HttpMethod.GET)
                .path("/api/store/customers/CUST-1/points")
                .retryable(retryable)
                .build();
    }

    @Test
    @DisplayName("deserializuje odpowiedz i dokleja naglowki Accept oraz Authorization")
    void deserializesResponseAndSendsHeaders() {
        server.enqueueJson(200, """
                {"customerId":7,"customerNumber":"CUST-1","pendingPoints":10,"availablePoints":90,"expiredPoints":0}""");

        try (HttpTransport transport = transport(RetryPolicy.none(), new BasicAuthentication("ecom", "secret"))) {
            PointsBalance balance = transport.execute(get(true), BALANCE);

            assertEquals(7L, balance.getCustomerId());
            assertEquals(90, balance.getAvailablePoints());
        }

        MockApiServer.RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.method());
        assertEquals("/api/store/customers/CUST-1/points", request.path());
        assertEquals("application/json", request.header("Accept"));
        assertEquals("Basic ZWNvbTpzZWNyZXQ=", request.header("Authorization"));
    }

    @Test
    @DisplayName("koduje parametry zapytania, zeby znaki specjalne nie rozjechaly routingu")
    void encodesQueryParameters() {
        server.enqueueJson(200, "{}");

        try (HttpTransport transport = transport(RetryPolicy.none(), null)) {
            transport.execute(ApiRequest.builder()
                    .method(HttpMethod.GET)
                    .path("/api/coupon/validate")
                    .queryParam("couponCode", "PL ABC/123")
                    .queryParam("customerNumber", "CUST-1")
                    .build(), null);
        }

        assertEquals("couponCode=PL%20ABC%2F123&customerNumber=CUST-1", server.takeRequest().query());
    }

    @Test
    @DisplayName("ponawia zadanie oznaczone jako bezpieczne po HTTP 503")
    void retriesRetryableRequestOnServerError() {
        server.enqueueEmpty(503);
        server.enqueueJson(200, """
                {"customerNumber":"CUST-1","availablePoints":5}""");

        try (HttpTransport transport = transport(fastRetry(3), null)) {
            PointsBalance balance = transport.execute(get(true), BALANCE);
            assertEquals(5, balance.getAvailablePoints());
        }

        assertEquals(2, server.receivedRequestCount());
    }

    @Test
    @DisplayName("nie ponawia zadania nieidempotentnego — rejestracja sprzedazy leci raz")
    void doesNotRetryNonRetryableRequest() {
        server.enqueueEmpty(503);
        server.enqueueJson(200, "{}");

        try (HttpTransport transport = transport(fastRetry(3), null)) {
            assertThrows(ServerException.class, () -> transport.execute(get(false), BALANCE));
        }

        assertEquals(1, server.receivedRequestCount());
    }

    @Test
    @DisplayName("konczy sie bledem po wyczerpaniu prob")
    void failsAfterExhaustingAttempts() {
        server.enqueueEmpty(503).enqueueEmpty(503).enqueueEmpty(503);

        try (HttpTransport transport = transport(fastRetry(3), null)) {
            ServerException exception = assertThrows(ServerException.class, () -> transport.execute(get(true), BALANCE));
            assertEquals(503, exception.getStatusCode());
        }

        assertEquals(3, server.receivedRequestCount());
    }

    @Test
    @DisplayName("po HTTP 401 odswieza poswiadczenia i ponawia raz, nie zuzywajac puli retry")
    void refreshesCredentialsOnUnauthorized() {
        server.enqueueEmpty(401);
        server.enqueueJson(200, """
                {"customerNumber":"CUST-1","availablePoints":42}""");

        AtomicInteger refreshCount = new AtomicInteger();
        AtomicInteger tokenVersion = new AtomicInteger(1);
        AuthenticationProvider authentication = new AuthenticationProvider() {
            @Override
            public void authorize(HttpRequest.Builder requestBuilder) {
                requestBuilder.header("Authorization", "Bearer token-" + tokenVersion.get());
            }

            @Override
            public boolean refreshAfterUnauthorized() {
                refreshCount.incrementAndGet();
                tokenVersion.incrementAndGet();
                return true;
            }
        };

        // maxAttempts=1 wylacza zwykle ponowienia — powtorzenie moze wynikac tylko z odswiezenia tokenu.
        try (HttpTransport transport = transport(RetryPolicy.none(), authentication)) {
            assertEquals(42, transport.execute(get(false), BALANCE).getAvailablePoints());
        }

        assertEquals(1, refreshCount.get());
        assertEquals("Bearer token-1", server.takeRequest().header("Authorization"));
        assertEquals("Bearer token-2", server.takeRequest().header("Authorization"));
    }

    @Test
    @DisplayName("nie wpada w petle, gdy odswiezone poswiadczenia dalej daja 401")
    void doesNotLoopWhenRefreshedCredentialsStillFail() {
        server.enqueueEmpty(401).enqueueEmpty(401);

        AuthenticationProvider authentication = new AuthenticationProvider() {
            @Override
            public void authorize(HttpRequest.Builder requestBuilder) {
                requestBuilder.header("Authorization", "Bearer stale");
            }

            @Override
            public boolean refreshAfterUnauthorized() {
                return true;
            }
        };

        try (HttpTransport transport = transport(RetryPolicy.none(), authentication)) {
            assertThrows(UnauthorizedException.class, () -> transport.execute(get(false), BALANCE));
        }

        assertEquals(2, server.receivedRequestCount());
    }

    @Test
    @DisplayName("mapuje ProblemDetail walidacji na BadRequestException z bledami pol")
    void mapsValidationProblemDetail() {
        server.enqueueProblem(400, """
                {"type":"about:blank","title":"Bad Request","status":400,"detail":"Validation failed",
                 "errors":{"customerNumber":"Customer number is required"}}""");

        try (HttpTransport transport = transport(RetryPolicy.none(), null)) {
            BadRequestException exception = assertThrows(BadRequestException.class,
                    () -> transport.execute(get(false), BALANCE));

            assertEquals(400, exception.getStatusCode());
            assertEquals("Validation failed", exception.getDetail().orElseThrow());
            assertEquals("Customer number is required", exception.getFieldErrors().get("customerNumber"));
            assertTrue(exception.getMessage().contains("customerNumber"));
        }
    }

    @Test
    @DisplayName("mapuje bledy biznesowe 400 na komunikat z pola detail")
    void mapsBusinessProblemDetail() {
        server.enqueueProblem(400, """
                {"status":400,"detail":"sourceTransactionNumber must be unique"}""");

        try (HttpTransport transport = transport(RetryPolicy.none(), null)) {
            BadRequestException exception = assertThrows(BadRequestException.class,
                    () -> transport.execute(get(false), BALANCE));

            assertEquals("sourceTransactionNumber must be unique", exception.getDetail().orElseThrow());
            assertTrue(exception.getFieldErrors().isEmpty());
        }
    }

    @Test
    @DisplayName("mapuje 403 i 404 na dedykowane wyjatki")
    void mapsForbiddenAndNotFound() {
        server.enqueueProblem(403, """
                {"status":403,"detail":"Forbidden"}""");
        server.enqueueProblem(404, """
                {"status":404,"detail":"Customer not found for customerNumber: CUST-1"}""");

        try (HttpTransport transport = transport(RetryPolicy.none(), null)) {
            assertThrows(ForbiddenException.class, () -> transport.execute(get(false), BALANCE));

            NotFoundException notFound = assertThrows(NotFoundException.class,
                    () -> transport.execute(get(false), BALANCE));
            assertTrue(notFound.getDetail().orElseThrow().contains("CUST-1"));
        }
    }

    @Test
    @DisplayName("radzi sobie z odpowiedzia bledu, ktora nie jest dokumentem ProblemDetail")
    void handlesNonProblemErrorBody() {
        server.enqueueJson(401, "<html>401</html>");

        try (HttpTransport transport = transport(RetryPolicy.none(), null)) {
            UnauthorizedException exception = assertThrows(UnauthorizedException.class,
                    () -> transport.execute(get(false), BALANCE));

            assertTrue(exception.getDetail().isEmpty());
            assertEquals("<html>401</html>", exception.getRawBody());
        }
    }

    @Test
    @DisplayName("ucina koncowy ukosnik w adresie bazowym, zeby sciezka nie zdublowala separatora")
    void normalizesBaseUri() {
        server.enqueueJson(200, "{}");

        try (HttpTransport transport = HttpTransport.builder()
                .baseUri(server.baseUrl() + "/")
                .retryPolicy(RetryPolicy.none())
                .build()) {
            transport.execute(get(false), BALANCE);
        }

        assertEquals("/api/store/customers/CUST-1/points", server.takeRequest().path());
    }

    private static RetryPolicy fastRetry(int maxAttempts) {
        return RetryPolicy.builder()
                .maxAttempts(maxAttempts)
                .initialBackoff(Duration.ofMillis(1))
                .maxBackoff(Duration.ofMillis(5))
                .build();
    }
}
