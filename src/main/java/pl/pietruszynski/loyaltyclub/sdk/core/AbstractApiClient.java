package pl.pietruszynski.loyaltyclub.sdk.core;

import lombok.Getter;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;

/**
 * Wspolna baza klientow API: trzyma transport i domyka pule polaczen.
 *
 * <p>Klient jest bezpieczny watkowo i przeznaczony do stworzenia raz na aplikacje.
 * Zamkniecie klienta zamyka pule polaczen tylko wtedy, gdy SDK samo ja utworzylo —
 * {@link HttpTransport.Builder#httpClient} dostarczony z zewnatrz pozostaje otwarty.
 */
public abstract class znaAbstractApiClient implements AutoCloseable {

    @Getter
    protected final HttpTransport transport;

    protected AbstractApiClient(HttpTransport transport) {
        this.transport = Validate.requireNonNull(transport, "transport");
    }

    @Override
    public void close() {
        transport.close();
    }
}
