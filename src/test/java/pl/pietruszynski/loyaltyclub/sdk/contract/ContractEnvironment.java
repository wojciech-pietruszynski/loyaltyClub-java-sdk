package pl.pietruszynski.loyaltyclub.sdk.contract;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Przygotowanie stanu wymaganego przez testy kontraktowe.
 *
 * <p>Uczestnik zakladany jest przez <b>przestrzen administracyjna</b>, ktorej biblioteka
 * celowo nie obsluguje -- stad zwykly klient HTTP zamiast SDK. Podzial jest zamierzony:
 * przygotowanie stanu nie moze korzystac z tego samego kodu, ktory jest przedmiotem
 * badania, bo wtedy blad w odwzorowaniu kontraktu znosilby sie sam i test przechodzilby
 * mimo rozjazdu.
 *
 * <p>Numer uczestnika niesie losowy przyrostek, zeby kolejne przebiegi nie kolidowaly
 * ze soba na tej samej bazie.
 */
final class ContractEnvironment {

    static final String BASE_URL_VARIABLE = "LOYALTYCLUB_CONTRACT_BASE_URL";

    private final HttpClient http = HttpClient.newHttpClient();

    private final String baseUrl;
    private final String adminUser;
    private final String adminPassword;
    private final String storeUser;
    private final String storePassword;
    private final String ecomUser;
    private final String ecomPassword;
    private final String customerNumber;

    ContractEnvironment() {
        baseUrl = zmienna(BASE_URL_VARIABLE, null);
        adminUser = zmienna("LOYALTYCLUB_CONTRACT_ADMIN_USER", "admin");
        adminPassword = zmienna("LOYALTYCLUB_CONTRACT_ADMIN_PASSWORD", null);
        storeUser = zmienna("LOYALTYCLUB_CONTRACT_STORE_USER", "store");
        storePassword = zmienna("LOYALTYCLUB_CONTRACT_STORE_PASSWORD", null);
        ecomUser = zmienna("LOYALTYCLUB_CONTRACT_ECOM_USER", "ecom");
        ecomPassword = zmienna("LOYALTYCLUB_CONTRACT_ECOM_PASSWORD", null);

        customerNumber = "CUST-KONTRAKT-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        zalozUczestnika();
    }

    String baseUrl() {
        return baseUrl;
    }

    String storeUser() {
        return storeUser;
    }

    String storePassword() {
        return storePassword;
    }

    String ecomUser() {
        return ecomUser;
    }

    String ecomPassword() {
        return ecomPassword;
    }

    /** Numer uczestnika zalozonego na potrzeby tego przebiegu. */
    String customerNumber() {
        return customerNumber;
    }

    private static String zmienna(String nazwa, String domyslna) {
        String wartosc = System.getenv(nazwa);
        if (wartosc != null && !wartosc.isBlank()) {
            return wartosc;
        }
        if (domyslna != null) {
            return domyslna;
        }
        throw new IllegalStateException(
                "Ustaw zmienna " + nazwa + " -- haslo konta nie ma wartosci domyslnej.");
    }

    private void zalozUczestnika() {
        String token = zalogujAdmina();
        String tresc = """
                {"firstName":"Anna","lastName":"Kowalska","email":"%s@example.invalid",\
                "customerNumber":"%s","phoneNumber":"+48123456789","country":"PL","loyaltyPoints":0}"""
                .formatted(customerNumber.toLowerCase(), customerNumber);

        HttpResponse<String> odpowiedz = wyslij(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/customers"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(tresc))
                .build());

        if (odpowiedz.statusCode() >= 300) {
            throw new IllegalStateException("Nie udalo sie zalozyc uczestnika ("
                    + odpowiedz.statusCode() + "): " + odpowiedz.body());
        }
    }

    private String zalogujAdmina() {
        HttpResponse<String> odpowiedz = wyslij(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/admin/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + adminUser + "\",\"password\":\"" + adminPassword + "\"}"))
                .build());

        if (odpowiedz.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Logowanie administratora nie powiodlo sie (" + odpowiedz.statusCode() + ").");
        }
        return odczytajToken(odpowiedz.body());
    }

    /**
     * Wyciaga token bez uzycia biblioteki JSON. Przygotowanie stanu ma byc niezalezne od
     * kodu, ktory testy sprawdzaja -- takze od jego konfiguracji serializacji.
     */
    private static String odczytajToken(String json) {
        int klucz = json.indexOf("\"token\"");
        if (klucz < 0) {
            throw new IllegalStateException("Odpowiedz logowania nie zawiera tokenu: " + json);
        }
        int poczatek = json.indexOf('"', json.indexOf(':', klucz)) + 1;
        int koniec = json.indexOf('"', poczatek);
        return json.substring(poczatek, koniec);
    }

    private HttpResponse<String> wyslij(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Backend jest nieosiagalny pod adresem " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Przerwano przygotowanie srodowiska testowego", e);
        }
    }
}
