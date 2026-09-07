package pl.pietruszynski.loyaltyclub.sdk.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.pietruszynski.loyaltyclub.sdk.core.retry.RetryPolicy;
import pl.pietruszynski.loyaltyclub.sdk.ecom.EcomClient;
import pl.pietruszynski.loyaltyclub.sdk.store.StoreClient;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sesja tokenowa prowadzona przez SDK: logowanie, przedluzanie przez {@code /refresh}
 * i wycofanie tokenu przez {@code /logout}. Zachowanie jest wspolne dla kasy i sklepu
 * internetowego, wiec obie przestrzenie sa sprawdzane w jednym miejscu.
 */
class JwtSessionTest {

    private MockApiServer server;

    @BeforeEach
    void startServer() {
        server = MockApiServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private StoreClient storeClient() {
        return StoreClient.builder()
                .baseUrl(server.baseUrl())
                .credentials("kasa-01", "haslo")
                .defaultCountryCode("PL")
                .retryPolicy(RetryPolicy.none())
                .requestTimeout(Duration.ofSeconds(5))
                .build();
    }

    private EcomClient ecomClient() {
        return EcomClient.builder()
                .baseUrl(server.baseUrl())
                .credentials("ecom-shop", "haslo")
                .retryPolicy(RetryPolicy.none())
                .requestTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Token wygasajacy wewnatrz domyslnego zapasu 60 s, wiec kolejne zadanie musi go wymienic. */
    private void enqueueShortLivedToken(String token, String role) {
        server.enqueueJson(200, """
                {"token":"%s","expiresAt":%d,"role":"%s","country":null}"""
                .formatted(token, Instant.now().plusSeconds(30).toEpochMilli(), role));
    }

    private void enqueueToken(String token, String role) {
        server.enqueueJson(200, """
                {"token":"%s","expiresAt":%d,"role":"%s","country":null}"""
                .formatted(token, Instant.now().plusSeconds(900).toEpochMilli(), role));
    }

    private void enqueueBalance() {
        server.enqueueJson(200, """
                {"customerNumber":"CUST-000123","availablePoints":7}""");
    }

    @Test
    @DisplayName("sklep internetowy loguje sie przez /api/ecom/auth/login zamiast wysylac haslo w kazdym zadaniu")
    void ecomLogsInWithToken() {
        enqueueToken("jwt-ecom", "ECOM");
        enqueueBalance();

        try (EcomClient client = ecomClient()) {
            assertEquals(7, client.getPointsBalance("CUST-000123").getAvailablePoints());
        }

        MockApiServer.RecordedRequest login = server.takeRequest();
        assertEquals("POST", login.method());
        assertEquals("/api/ecom/auth/login", login.path());
        assertTrue(login.body().contains("\"username\":\"ecom-shop\""));

        // Haslo poszlo po sieci raz, przy logowaniu — odczyt niesie juz tylko token.
        assertEquals("Bearer jwt-ecom", server.takeRequest().header("Authorization"));
        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("token blisko wygasniecia jest przedluzany przez /refresh, bez ponownego logowania haslem")
    void refreshesTokenInsteadOfLoggingInAgain() {
        enqueueShortLivedToken("jwt-krotki", "STORE");
        enqueueBalance();
        enqueueToken("jwt-przedluzony", "STORE");
        enqueueBalance();
        enqueueBalance();

        try (StoreClient client = storeClient()) {
            client.getPointsBalance("CUST-000123");
            client.getPointsBalance("CUST-000123");
            client.getPointsBalance("CUST-000123");
        }

        assertEquals("/api/store/auth/login", server.takeRequest().path());
        assertEquals("Bearer jwt-krotki", server.takeRequest().header("Authorization"));

        MockApiServer.RecordedRequest refresh = server.takeRequest();
        assertEquals("POST", refresh.method());
        assertEquals("/api/store/auth/refresh", refresh.path());
        assertEquals("Bearer jwt-krotki", refresh.header("Authorization"));
        // Odswiezenie okazuje token, a nie poswiadczenia — haslo nie opuszcza juz pamieci klienta.
        assertTrue(refresh.body().isEmpty());

        assertEquals("Bearer jwt-przedluzony", server.takeRequest().header("Authorization"));
        // Trzeci odczyt korzysta z dlugo zyjacego tokenu, wiec zadnego wywolania auth juz nie ma.
        assertEquals("Bearer jwt-przedluzony", server.takeRequest().header("Authorization"));
    }

    @Test
    @DisplayName("odrzucone odswiezenie konczy sie cichym ponownym logowaniem")
    void fallsBackToLoginWhenRefreshIsRejected() {
        enqueueShortLivedToken("jwt-wycofany", "STORE");
        enqueueBalance();
        server.enqueueEmpty(401);
        enqueueToken("jwt-nowy", "STORE");
        enqueueBalance();

        try (StoreClient client = storeClient()) {
            client.getPointsBalance("CUST-000123");
            assertEquals(7, client.getPointsBalance("CUST-000123").getAvailablePoints());
        }

        assertEquals("/api/store/auth/login", server.takeRequest().path());
        server.takeRequest();
        assertEquals("/api/store/auth/refresh", server.takeRequest().path());
        assertEquals("/api/store/auth/login", server.takeRequest().path());
        assertEquals("Bearer jwt-nowy", server.takeRequest().header("Authorization"));
    }

    @Test
    @DisplayName("logout wycofuje token, a kolejne zadanie loguje sie od nowa")
    void logoutRevokesTokenAndNextCallLogsInAgain() {
        enqueueToken("jwt-pierwszy", "STORE");
        enqueueBalance();
        server.enqueueEmpty(204);
        enqueueToken("jwt-drugi", "STORE");
        enqueueBalance();

        try (StoreClient client = storeClient()) {
            client.getPointsBalance("CUST-000123");
            assertTrue(client.logout());
            client.getPointsBalance("CUST-000123");
        }

        assertEquals("/api/store/auth/login", server.takeRequest().path());
        server.takeRequest();

        MockApiServer.RecordedRequest logout = server.takeRequest();
        assertEquals("POST", logout.method());
        assertEquals("/api/store/auth/logout", logout.path());
        assertEquals("Bearer jwt-pierwszy", logout.header("Authorization"));

        assertEquals("/api/store/auth/login", server.takeRequest().path());
        assertEquals("Bearer jwt-drugi", server.takeRequest().header("Authorization"));
    }

    @Test
    @DisplayName("logout przy HTTP Basic nie ma czego wycofac i nie idzie w siec")
    void logoutIsNoOpWithoutTokenSession() {
        try (EcomClient client = EcomClient.builder()
                .baseUrl(server.baseUrl())
                .basicAuth("ecom-shop", "haslo")
                .retryPolicy(RetryPolicy.none())
                .build()) {

            assertFalse(client.logout());
        }

        assertEquals(0, server.receivedRequestCount());
    }

    @Test
    @DisplayName("logout bez rozpoczetej sesji nie wysyla zadania")
    void logoutWithoutStartedSessionSendsNothing() {
        try (StoreClient client = storeClient()) {
            assertTrue(client.logout());
        }

        assertEquals(0, server.receivedRequestCount());
    }
}
