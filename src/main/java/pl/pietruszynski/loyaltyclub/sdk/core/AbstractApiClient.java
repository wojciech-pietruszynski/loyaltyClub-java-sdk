package pl.pietruszynski.loyaltyclub.sdk.core;

import lombok.Getter;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.JwtLoginAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;

/**
 * Wspolna baza klientow API: trzyma transport i domyka pule polaczen.
 *
 * <p>Klient jest bezpieczny watkowo i przeznaczony do stworzenia raz na aplikacje.
 * Zamkniecie klienta zamyka pule polaczen tylko wtedy, gdy SDK samo ja utworzylo —
 * {@link HttpTransport.Builder#httpClient} dostarczony z zewnatrz pozostaje otwarty.
 */
public abstract class AbstractApiClient implements AutoCloseable {

    @Getter
    protected final HttpTransport transport;

    protected AbstractApiClient(HttpTransport transport) {
        this.transport = Validate.requireNonNull(transport, "transport");
    }

    /**
     * Wycofuje token sesji przez {@code POST /api/{kanal}/auth/logout} i czysci go z pamieci.
     *
     * <p>Ma znaczenie tylko dla klienta prowadzacego sesje tokenowa (builder z
     * {@code credentials(...)}) — dla HTTP Basic i tokenu podanego z zewnatrz nie ma czego
     * wycofywac i wywolanie nic nie robi. Wylogowanie jest idempotentne, a kolejne zadanie
     * na tym kliencie zaloguje sie ponownie.
     *
     * <p>Nie jest wolane przez {@link #close()}: zamkniecie klienta konczy zycie puli polaczen,
     * a uniewaznienie tokenu to decyzja integracji — dlugo zyjaca kasa restartuje klienta,
     * ale sesji zamykac nie chce.
     *
     * @return {@code true}, jesli byla sesja do wycofania
     */
    public boolean logout() {
        if (transport.getAuthentication() instanceof JwtLoginAuthentication jwtAuthentication) {
            jwtAuthentication.logout();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        transport.close();
    }
}
