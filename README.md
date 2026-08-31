# LoyaltyClub Java SDK

SDK w czystej Javie do API programu lojalnościowego **LoyaltyClub** — obsługuje integrację
kasową (`/api/store/**`) oraz e-commerce (`/api/ecom/**` i `/api/coupon/**`).

- **Java 25**, jeden artefakt Maven, brak Springa
- transport: `java.net.http.HttpClient` z JDK
- jedyne zależności runtime: `jackson-databind` + `jackson-datatype-jsr310`
- Lombok tylko na czas kompilacji (`provided`) — nie trafia do classpath aplikacji
- sesja JWT prowadzona przez SDK dla obu kanałów: logowanie, przedłużanie przez `/refresh`
  i wycofanie tokenu przez `/logout`
- ponowienia z wykładniczym backoffem, wyłącznie dla operacji bezpiecznych do powtórzenia
- walidacja żądań po stronie klienta, zanim pójdzie round-trip po HTTP 400

---

## Dokumentacja techniczna

Pełne opracowanie — analiza kontraktu API, rejestr decyzji architektonicznych, diagramy
architektury i przepływów, model niezawodności, zakres weryfikacji oraz omówienie korzyści
inżynierskich ze stosowania SDK — znajduje się w [`docs/dokumentacja-sdk.html`](docs/dokumentacja-sdk.html).
Plik jest samodzielną stroną: wystarczy otworzyć go w przeglądarce. Zawiera arkusz stylów
dla druku, więc nadaje się też do eksportu do PDF.

Ten plik README pozostaje krótkim przewodnikiem użycia; dokument w `docs/` jest źródłem
opisu biblioteki na potrzeby dokumentacji projektowej.

---

## Instalacja

```bash
mvn clean install
```

Następnie w projekcie hosta:

```xml
<dependency>
    <groupId>pl.pietruszynski.loyaltyclub</groupId>
    <artifactId>loyaltyclub-java-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Wymagania: **JDK 25** i Maven 3.9+.

---

## Store — API kasowe

Rola `STORE`. SDK loguje się przez `POST /api/store/auth/login` i samo przedłuża sesję
przez `POST /api/store/auth/refresh`, zanim token wygaśnie (backend daje 15 minut).
Hasło idzie po sieci raz — przy pierwszym logowaniu. Po odpowiedzi 401 SDK loguje się ponownie.

```java
try (StoreClient store = StoreClient.builder()
        .baseUrl("http://localhost:8089")
        .credentials("kasa-01", "haslo")
        .defaultCountryCode("PL")
        .build()) {

    StoreTransactionResponse sale = store.registerSale(StoreSaleRequest.builder()
            .customerNumber("CUST-000123")
            .sourceTransactionNumber("POS-2026-08-28-0001")
            .totalAmount(new BigDecimal("59.98"))
            .purchaseTimestamp(LocalDateTime.now())
            .item(StoreTransactionItem.builder()
                    .cartPosition("1")
                    .ean("5901234123457")
                    .name("Kawa ziarnista 1 kg")
                    .hierarchy(Hierarchy.builder().hierarchy("FOOD").productClass("COFFEE").build())
                    .price(ItemPrice.builder().amount(new BigDecimal("59.98")).currency("PLN").build())
                    .build())
            .build());

    System.out.println(sale.getPoints() + " pkt, dostępne od " + sale.getAvailableFrom());

    PointsBalance balance = store.getPointsBalance("CUST-000123");
}
```

| Metoda | Endpoint |
|---|---|
| `info()` | `GET /api/store` |
| `registerSale(request)` / `registerSale(countryCode, request)` | `POST /api/store/transactions/sale` |
| `registerReturn(request)` / `registerReturn(countryCode, request)` | `POST /api/store/transactions/return` |
| `getPointsBalance(customerNumber)` | `GET /api/store/customers/{customerNumber}/points` |
| `logout()` | `POST /api/store/auth/logout` |

Nagłówek `X-CountryCode` jest doklejany automatycznie — z `defaultCountryCode` albo
z jawnego parametru. Kod kraju jest normalizowany (trim + wielkie litery) i sprawdzany
pod kątem limitu 3 znaków, tak jak robi to backend.

Zwrot dodatkowo wymaga numeru pierwotnej sprzedaży:

```java
store.registerReturn(StoreReturnRequest.builder()
        .customerNumber("CUST-000123")
        .sourceTransactionNumber("POS-2026-08-28-0002")
        .saleTransactionNumber("POS-2026-08-28-0001")
        .totalAmount(new BigDecimal("59.98"))
        .item(/* ... */)
        .build());
```

Alternatywne uwierzytelnienie (backend akceptuje oba): `.basicAuth("kasa-01", "haslo")`
albo `.bearerToken(token)` dla tokenu zdobytego poza SDK.

`store.logout()` wycofuje token przez `POST /api/store/auth/logout` — backend odnotowuje go
jako unieważniony, więc przestaje działać przed końcem swojego czasu życia. Kolejne żądanie
na tym samym kliencie zaloguje się od nowa. `close()` **nie** wywołuje wylogowania: zamknięcie
klienta kończy życie puli połączeń, a unieważnienie sesji to osobna decyzja integracji.

---

## E-commerce — API odczytowe i kupony

Rola `ECOM`. Backend wystawia dla tej roli komplet punktów logowania
(`/api/ecom/auth/login`, `/refresh`, `/logout`), więc domyślną drogą jest `credentials(...)`
i sesja tokenowa — dokładnie jak dla kasy. `basicAuth(...)` nadal działa, ale przesyła hasło
przy każdym żądaniu, więc zostaje wariantem awaryjnym; `bearerToken(...)` zostaje dla
integracji, które zdobywają JWT własnym kanałem.

```java
try (EcomClient ecom = EcomClient.builder()
        .baseUrl("http://localhost:8089")
        .credentials("ecom-shop", "haslo")
        .build()) {

    EcomCustomerProfile profile = ecom.getCustomerProfile("CUST-000123");
    PointsBalance balance = ecom.getPointsBalance("CUST-000123");
    List<CustomerTransaction> history = ecom.getTransactions("CUST-000123");
    List<CustomerCoupon> coupons = ecom.getCoupons("CUST-000123");

    // Konta z długą historią czyta się stroną po stronie.
    PageResponse<CustomerTransaction> page = ecom.getTransactionsPage("CUST-000123", 0, 50);
    while (page.hasNext()) {
        page = ecom.getTransactionsPage("CUST-000123", page.nextPage(), 50);
    }

    // Kupony korzystają z tych samych poświadczeń i tej samej puli połączeń.
    CouponValidationResponse validation = ecom.coupons().validate("PL-ABC123", "CUST-000123");
    if (validation.isValid()) {
        BigDecimal discount = validation.getDefinition().getCouponValue();
    }

    CouponRedeemResponse redeemed = ecom.coupons().redeemPoints(
            "order-2026-08-28-0042",                       // klucz idempotentności
            CouponRedeemRequest.builder()
                    .customerNumber("CUST-000123")
                    .couponTemplateId(3L)
                    .build());
}
```

| Metoda | Endpoint |
|---|---|
| `EcomClient.info()` | `GET /api/ecom` |
| `getPointsBalance(cn)` | `GET /api/ecom/customers/{cn}/points` |
| `getCustomerProfile(cn)` | `GET /api/ecom/customers/{cn}/profile` |
| `getTransactions(cn)` | `GET /api/ecom/customers/{cn}/transactions` |
| `getTransactionsPage(cn, page, size)` | `GET /api/ecom/customers/{cn}/transactions/paged` |
| `getCoupons(cn)` | `GET /api/ecom/customers/{cn}/coupons` |
| `getCouponsPage(cn, page, size)` | `GET /api/ecom/customers/{cn}/coupons/paged` |
| `logout()` | `POST /api/ecom/auth/logout` |
| `CouponClient.redeemPoints(key, request)` | `POST /api/coupon/redeem-points` |
| `CouponClient.validate(code, cn)` | `GET /api/coupon/validate` |

`CouponClient` da się też zbudować samodzielnie, bez `EcomClient`:

```java
CouponClient coupons = CouponClient.builder()
        .baseUrl("http://localhost:8089")
        .credentials("ecom-shop", "haslo")
        .build();
```

### Stronicowanie

Warianty `...Page(cn, page, size)` odpowiadają endpointom `.../paged` backendu i zwracają
`PageResponse<T>` — z `content`, `totalElements`, `totalPages` oraz skrótami `hasNext()`
i `nextPage()`. `page` liczy się od zera; `null` w obu parametrach zostawia backendowi jego
wartości domyślne (strona 0, rozmiar 25). Limit `size` to **200** — SDK sprawdza go lokalnie,
bo naruszenie i tak skończyłoby się błędem 400.

Wariant stronicowany sortuje malejąco (najnowsze pierwsze); wariant listowy zwraca całą
kolekcję jednym żądaniem, historia transakcji rosnąco po dacie.

### Klucz idempotentności

`redeemPoints` wymaga jawnego klucza, bo backend realnie po nim deduplikuje: powtórzenie
z tym samym kluczem zwraca ten sam kupon, zamiast pobrać punkty drugi raz. Dlatego klucz
musi być **stabilny dla jednej próby biznesowej** — identyfikator zamówienia albo akcji
w sklepie, nie świeży UUID przy każdej próbie. SDK celowo go nie generuje: wygenerowany
losowo klucz zamieniłby zabezpieczenie w atrapę.

### Walidacja kuponu to nie błąd HTTP

Kupon nieważny wraca jako **HTTP 200** z werdyktem w polu `status`
(`CouponValidationStatus`). Sprawdzaj `validation.isValid()`, nie kod odpowiedzi.

---

## Obsługa błędów

Wszystkie wyjątki dziedziczą po `LoyaltyClubException` (unchecked).

| Wyjątek | Kiedy |
|---|---|
| `LoyaltyClubValidationException` | żądanie odrzucone lokalnie, **przed** wysłaniem |
| `BadRequestException` (400) | walidacja bean validation **oraz** błędy biznesowe backendu |
| `UnauthorizedException` (401) | brak lub nieważne poświadczenia |
| `ForbiddenException` (403) | rola bez dostępu do namespace'u |
| `NotFoundException` (404) | np. nieznany `customerNumber` |
| `ServerException` (5xx) | błąd backendu, po wyczerpaniu ponowień |
| `LoyaltyClubTransportException` | brak połączenia, timeout, przerwanie wątku |
| `LoyaltyClubSerializationException` | błąd (de)serializacji JSON |

Backend zwraca RFC 7807, więc szczegóły są dostępne wprost:

```java
try {
    store.registerSale(request);
} catch (BadRequestException e) {
    e.getDetail().ifPresent(System.out::println);   // "sourceTransactionNumber must be unique"
    e.getFieldErrors().forEach(...);                // {"items": "Items are required"}
    e.getStatusCode();
    e.getProblemDetail();
}
```

> **Uwaga na kontrakt backendu:** `GlobalExceptionHandler` mapuje każdy `RuntimeException`
> na **400**, więc błędy biznesowe (przekroczona kwota zwrotu, zwrot punktów wygasłych,
> duplikat numeru transakcji) przychodzą tym samym kodem, co błędy walidacji. Rozróżniaj je
> po `getFieldErrors()` (puste = błąd biznesowy) i po treści `getDetail()`.

---

## Ponowienia

Domyślnie 3 próby, backoff 200 ms → 2 s z jitterem, dla kodów `408, 425, 429, 500, 502, 503, 504`
oraz błędów I/O.

Ponawiane są **wyłącznie operacje bezpieczne do powtórzenia**:

| Operacja | Ponawiana | Dlaczego |
|---|---|---|
| wszystkie `GET` | tak | odczyt bez efektów ubocznych |
| logowanie sklepu | tak | nie zmienia stanu biznesowego |
| `redeemPoints` | tak | chroniona nagłówkiem `Idempotency-Key` |
| `registerSale` / `registerReturn` | **nie** | przy błędzie sieci nie wiadomo, czy transakcja została zapisana |

Po `LoyaltyClubTransportException` przy sprzedaży ponów żądanie **z tym samym**
`sourceTransactionNumber` — backend wymusza jego unikalność, więc duplikat skończy się
błędem 400 zamiast podwójnym naliczeniem punktów.

Własna polityka:

```java
StoreClient.builder()
        .retryPolicy(RetryPolicy.builder()
                .maxAttempts(5)
                .initialBackoff(Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(5))
                .build())
        // RetryPolicy.none() wyłącza ponawianie całkowicie
        .build();
```

---

## Konfiguracja klienta

Wspólne dla obu klientów:

```java
StoreClient.builder()
        .baseUrl("https://loyalty.example.com")
        .connectTimeout(Duration.ofSeconds(5))     // domyślnie 10 s
        .requestTimeout(Duration.ofSeconds(15))    // domyślnie 30 s
        .retryPolicy(RetryPolicy.defaultPolicy())
        .httpClient(wlasnyHttpClient)              // własna pula połączeń
        .objectMapper(wlasnyMapper)                // własny mapper JSON
        .defaultHeader("X-Correlation-Id", "...")  // nagłówek doklejany do każdego żądania
        .userAgent("moj-system/2.1")
        .build();
```

Klient jest **bezpieczny wątkowo** — twórz go raz na aplikację i współdziel; `HttpClient`
utrzymuje wtedy pulę połączeń. `close()` zamyka pulę tylko wtedy, gdy SDK samo ją utworzyło —
`HttpClient` podany przez `httpClient(...)` zostaje nietknięty.

---

## Walidacja po stronie klienta

Zanim żądanie pójdzie w sieć, SDK sprawdza to, co i tak sprawdzi backend:

- `customerNumber`, `sourceTransactionNumber`, `saleTransactionNumber` — niepuste
- `items` — niepusta lista, każda pozycja z `ean`, `name`, `hierarchy.hierarchy`,
  `price.amount` (nieujemna) i `price.currency`
- `totalAmount` — dodatnia i **równa sumie cen pozycji** po zaokrągleniu do 2 miejsc
  w trybie HALF_UP (dokładnie ta sama normalizacja, co w `StoreTransactionService`)
- `countryCode` — niepusty, maks. 3 znaki
- `idempotencyKey`, `couponTemplateId`, `couponCode` — niepuste;
  `idempotencyKey` dodatkowo maks. 100 znaków (limit backendu)
- `page` — nieujemny, `size` — z zakresu `1..200` (limit `PageRequests` w backendzie)

Naruszenie kończy się `LoyaltyClubValidationException` **bez wywołania sieciowego**.

---

## Kompatybilność w przód

- nieznane pola w odpowiedziach są ignorowane
- nieznane wartości enumów mapują się na `UNKNOWN` zamiast wysadzać deserializację
  (`TransactionType`, `TransactionState`, `CustomerStatus`, `CouponStatus`, `CouponReason`,
  `CouponValidationStatus`) — nowy werdykt kuponu traktuj jak odmowę
- `LocalDateTime` jedzie jako ISO-8601 bez strefy, zgodnie z domyślną konfiguracją Jacksona
  po stronie Spring Boota

---

## Struktura projektu

```
src/main/java/pl/pietruszynski/loyaltyclub/sdk/
├── core/                 wspólny fundament
│   ├── http/             HttpTransport, ApiRequest, HttpMethod
│   ├── auth/             Basic, Bearer, JwtLoginAuthentication (login/refresh/logout)
│   ├── retry/            RetryPolicy
│   ├── exception/        hierarchia wyjątków
│   ├── model/            PointsBalance, ServiceInfo, ProblemDetail, PageResponse,
│   │                     LoginRequest/LoginResponse, TransactionType/State, CustomerStatus
│   ├── json/             LoyaltyClubJson
│   └── util/             Validate, Uris
├── store/                StoreClient, StoreRequestValidator, StoreJwtAuthentication, modele
└── ecom/                 EcomClient, CouponClient, EcomJwtAuthentication, modele
```

Typy dzielone przez oba kanały mieszkają w `core/model` — backend zwraca ten sam kształt
z `/api/store` i z `/api/ecom`, więc SDK nie trzyma dwóch kopii tego samego enuma.

## Testy

```bash
mvn test
```

60 testów na serwerze-atrapie opartym na `com.sun.net.httpserver` z JDK — bez dodatkowych
zależności testowych. Pokrywają transport (retry, mapowanie błędów, kodowanie URI), sesję
tokenową (logowanie, `/refresh`, powrót do logowania po odrzuconym odświeżeniu, `/logout`),
serializację JSON, stronicowanie oraz obie integracje.
