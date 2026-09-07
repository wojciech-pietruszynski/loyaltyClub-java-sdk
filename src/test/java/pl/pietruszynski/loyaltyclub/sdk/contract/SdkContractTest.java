package pl.pietruszynski.loyaltyclub.sdk.contract;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubApiException;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PageResponse;
import pl.pietruszynski.loyaltyclub.sdk.core.model.PointsBalance;
import pl.pietruszynski.loyaltyclub.sdk.core.model.ServiceInfo;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionState;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionType;
import pl.pietruszynski.loyaltyclub.sdk.ecom.EcomClient;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponValidationResponse;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CouponValidationStatus;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.CustomerTransaction;
import pl.pietruszynski.loyaltyclub.sdk.ecom.model.EcomCustomerProfile;
import pl.pietruszynski.loyaltyclub.sdk.store.StoreClient;
import pl.pietruszynski.loyaltyclub.sdk.store.model.Hierarchy;
import pl.pietruszynski.loyaltyclub.sdk.store.model.ItemPrice;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreSaleRequest;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreTransactionItem;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreTransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zgodnosc biblioteki z <b>dzialajaca</b> instancja backendu.
 *
 * <p>Testy jednostkowe sprawdzaja biblioteke wobec serwera atrapy, a atrapa odwzorowuje
 * to, czego test od niej oczekuje -- nie to, co naprawde wystawia backend. Rozjazd
 * kontraktu jest wiec dla niej niewidoczny i ujawnia sie dopiero u integratora, w momencie
 * najdrozszym do usuniecia. Ponizsze przypadki zamykaja te luke: kazdy przechodzi przez
 * siec do prawdziwego serwera i sprawdza ksztalt odpowiedzi, nie zas zgodnosc z wlasnym
 * wyobrazeniem o niej.
 *
 * <p>Zakres jest celowo waski. Testy nie powielaja regul dziedzinowych sprawdzanych przez
 * warstwe serwerowa -- pytaja wylacznie o to, czego atrapa potwierdzic nie moze: nazwy pol,
 * ksztalt koperty stronicowania, wartosci typow wyliczeniowych, postac dokumentu bledu
 * i dzialanie sesji tokenowej.
 *
 * <p>Klasa jest oznaczona znacznikiem {@code kontraktowy} i wykluczona z domyslnego
 * przebiegu, poniewaz wymaga infrastruktury. Uruchomienie:
 * <pre>
 *   docker compose up -d                      (w repozytorium backendu)
 *   export LOYALTYCLUB_CONTRACT_BASE_URL=http://localhost:8089
 *   mvn test -Pkontrakt
 * </pre>
 */
@Tag("kontraktowy")
@EnabledIfEnvironmentVariable(named = ContractEnvironment.BASE_URL_VARIABLE, matches = ".+",
        disabledReason = "Ustaw LOYALTYCLUB_CONTRACT_BASE_URL na adres dzialajacego backendu.")
class SdkContractTest {

    private static ContractEnvironment env;
    private static StoreClient store;
    private static EcomClient ecom;

    @BeforeAll
    static void przygotujSrodowisko() {
        env = new ContractEnvironment();
        store = StoreClient.builder()
                .baseUrl(env.baseUrl())
                .credentials(env.storeUser(), env.storePassword())
                .defaultCountryCode("PL")
                .build();
        ecom = EcomClient.builder()
                .baseUrl(env.baseUrl())
                .credentials(env.ecomUser(), env.ecomPassword())
                .build();
    }

    @AfterAll
    static void zamknijKlientow() {
        if (store != null) {
            store.close();
        }
        if (ecom != null) {
            ecom.close();
        }
    }

    private static StoreSaleRequest sprzedaz(String numerDokumentu) {
        return StoreSaleRequest.builder()
                .customerNumber(env.customerNumber())
                .sourceTransactionNumber(numerDokumentu)
                .totalAmount(new BigDecimal("150.00"))
                .purchaseTimestamp(LocalDateTime.of(2026, 9, 7, 10, 0))
                .items(List.of(StoreTransactionItem.builder()
                        .cartPosition("1")
                        .ean("5901234123457")
                        .name("Kawa")
                        .hierarchy(Hierarchy.builder()
                                .hierarchy("H1")
                                .productClass("NAPOJE")
                                .subclass("KAWA")
                                .build())
                        .price(ItemPrice.builder()
                                .amount(new BigDecimal("150.00"))
                                .currency("PLN")
                                .build())
                        .build()))
                .build();
    }

    private static String nowyNumerDokumentu() {
        return "POS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    @Test
    @DisplayName("sesja tokenowa ECOM dziala wobec zywego backendu")
    void ecomTokenSessionWorksAgainstLiveBackend() {
        // Samo powodzenie odczytu dowodzi, ze /api/ecom/auth/login istnieje, przyjmuje
        // ksztalt zadania wysylany przez SDK i zwraca token, ktory backend nastepnie
        // akceptuje. Zaden z tych trzech warunkow nie jest sprawdzalny wobec atrapy.
        PointsBalance saldo = ecom.getPointsBalance(env.customerNumber());

        assertEquals(env.customerNumber(), saldo.getCustomerNumber());
    }

    @Test
    @DisplayName("profil uczestnika ma pola oczekiwane przez biblioteke")
    void customerProfileMatchesLibraryModel() {
        EcomCustomerProfile profil = ecom.getCustomerProfile(env.customerNumber());

        assertEquals(env.customerNumber(), profil.getCustomerNumber());
        assertEquals("Anna", profil.getFirstName());
        assertEquals("PL", profil.getCountry());
        // Prog lojalnosciowy wyliczany jest po stronie serwera; puste pole oznaczaloby,
        // ze nazwa w JSON rozjechala sie z nazwa pola w modelu.
        assertNotNull(profil.getLoyaltyTierCode());
    }

    @Test
    @DisplayName("rejestracja sprzedazy zwraca transakcje w ksztalcie modelu biblioteki")
    void saleRegistrationMatchesLibraryModel() {
        StoreTransactionResponse odpowiedz = store.registerSale(sprzedaz(nowyNumerDokumentu()));

        assertEquals(env.customerNumber(), odpowiedz.getCustomerNumber());
        assertEquals(TransactionType.SALE, odpowiedz.getType());
        // Punkty ze sprzedazy sa najpierw oczekujace - stan wyliczany jest z dat po
        // stronie serwera, wiec wartosc przychodzi z backendu, a nie z SDK.
        assertEquals(TransactionState.PENDING, odpowiedz.getState());
        assertEquals(0, new BigDecimal("150.00").compareTo(odpowiedz.getAmount()));
    }

    @Test
    @DisplayName("koperta stronicowania ma ksztalt, ktorego oczekuje PageResponse")
    void pagedResponseEnvelopeMatchesLibraryModel() {
        store.registerSale(sprzedaz(nowyNumerDokumentu()));

        PageResponse<CustomerTransaction> strona =
                ecom.getTransactionsPage(env.customerNumber(), 0, 5);

        // Pola koperty: gdyby backend zmienil ktorakolwiek nazwe, deserializacja dalaby
        // wartosci domyslne i ponizsze asercje by tego nie przepuscily.
        assertFalse(strona.contentOrEmpty().isEmpty());
        assertEquals(0, strona.getPage());
        assertEquals(5, strona.getSize());
        assertTrue(strona.getTotalElements() > 0);
        assertTrue(strona.getTotalPages() > 0);

        CustomerTransaction transakcja = strona.contentOrEmpty().getFirst();
        assertEquals(TransactionType.SALE, transakcja.getType());
        assertNotEquals(TransactionState.UNKNOWN, transakcja.getState());
        assertNotNull(transakcja.getAmount());
        assertNotNull(transakcja.getExpiresAt());
    }

    @Test
    @DisplayName("nieznany kupon wraca jako werdykt w odpowiedzi 200, nie jako blad HTTP")
    void unknownCouponReturnsVerdictNotHttpError() {
        CouponValidationResponse wynik =
                ecom.coupons().validate("PL-NIE-ISTNIEJE", env.customerNumber());

        assertFalse(wynik.isValid());
        // Werdykt musi byc rozpoznany, a nie zmapowany na UNKNOWN - to sprawdza, czy zbior
        // wartosci po stronie biblioteki nadaza za backendem.
        assertNotNull(wynik.getStatus());
        assertNotEquals(CouponValidationStatus.UNKNOWN, wynik.getStatus());
    }

    @Test
    @DisplayName("nieznany uczestnik konczy sie wyjatkiem zbudowanym z dokumentu bledu")
    void unknownCustomerIsMappedFromProblemDocument() {
        LoyaltyClubApiException wyjatek = assertThrows(LoyaltyClubApiException.class,
                () -> ecom.getCustomerProfile("CUST-NIE-ISTNIEJE"));

        // Backend odpowiada dokumentem opisu bledu; biblioteka ma go odczytac, a nie
        // zwrocic samo "cos poszlo nie tak".
        assertTrue(wyjatek.getStatusCode() >= 400);
        assertNotNull(wyjatek.getProblemDetail());
    }

    @Test
    @DisplayName("powtorzony numer dokumentu kasowego nie tworzy drugiej transakcji")
    void repeatedSourceTransactionNumberIsRejected() {
        String numerDokumentu = nowyNumerDokumentu();
        store.registerSale(sprzedaz(numerDokumentu));

        // Odpornosc na powtorzenie oparta jest na ograniczeniu bazy danych, a nie na
        // sprawdzeniu w kodzie aplikacji - atrapa nie ma jak tego potwierdzic.
        assertThrows(LoyaltyClubApiException.class,
                () -> store.registerSale(sprzedaz(numerDokumentu)));
    }

    @Test
    @DisplayName("metadane integracji niosa wersje API")
    void serviceInfoCarriesApiVersion() {
        ServiceInfo info = ecom.info();

        assertNotNull(info.getApiVersion());
        assertFalse(info.getApiVersion().isBlank());
    }
}
