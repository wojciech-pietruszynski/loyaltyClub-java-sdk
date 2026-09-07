package pl.pietruszynski.loyaltyclub.sdk.ecom;

import pl.pietruszynski.loyaltyclub.sdk.core.AbstractClientBuilder;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.AuthenticationProvider;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.BasicAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.BearerTokenAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubValidationException;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;
import pl.pietruszynski.loyaltyclub.sdk.ecom.auth.EcomJwtAuthentication;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Poswiadczenia wspolne dla obu klientow e-commerce.
 *
 * <p>Zalecana droga to {@link #credentials(String, String)} — SDK loguje sie wtedy przez
 * {@code POST /api/ecom/auth/login} i samo prowadzi sesje tokenowa. HTTP Basic dziala nadal,
 * ale przesyla haslo przy kazdym zadaniu, wiec zostaje wariantem awaryjnym.
 *
 * @param <B> typ konkretnego buildera
 */
public abstract class EcomClientBuilderSupport<B extends EcomClientBuilderSupport<B>>
        extends AbstractClientBuilder<B> {

    private String username;
    private String password;
    private Duration tokenRefreshSkew;
    private AuthenticationProvider authentication;

    /**
     * Poswiadczenia konta z rola {@code ECOM}. SDK zaloguje sie przez
     * {@code POST /api/ecom/auth/login} i bedzie przedluzac sesje przed wygasnieciem tokenu.
     */
    public B credentials(String username, String password) {
        this.username = username;
        this.password = password;
        return self();
    }

    /**
     * Zapas czasu, z jakim token jest wymieniany przed wygasnieciem; domyslnie 60 s.
     * Token backendu zyje 15 minut.
     */
    public B tokenRefreshSkew(Duration tokenRefreshSkew) {
        this.tokenRefreshSkew = tokenRefreshSkew;
        return self();
    }

    /**
     * HTTP Basic z poswiadczeniami uzytkownika o roli {@code ECOM}. Backend akceptuje
     * ten wariant, ale haslo leci w kazdym zadaniu — wybieraj go tylko tam, gdzie sesja
     * tokenowa jest nie do utrzymania.
     */
    public B basicAuth(String username, String password) {
        this.authentication = new BasicAuthentication(username, password);
        return self();
    }

    /** Gotowy token JWT z rola {@code ECOM}, zdobyty poza SDK. */
    public B bearerToken(String token) {
        this.authentication = new BearerTokenAuthentication(token);
        return self();
    }

    /** Token JWT odczytywany przy kazdym zadaniu — dla integracji rotujacych tokeny. */
    public B bearerToken(Supplier<String> tokenSupplier) {
        this.authentication = new BearerTokenAuthentication(tokenSupplier);
        return self();
    }

    /** Wlasna implementacja zrodla poswiadczen. */
    public B authentication(AuthenticationProvider authentication) {
        this.authentication = authentication;
        return self();
    }

    /**
     * Sklada transport z wybranym zrodlem poswiadczen. Dla wariantu z hasłem tworzy sesje
     * tokenowa; logowanie idzie tym samym transportem, ale bez naglowka {@code Authorization},
     * stad referencja z opoznieniem — transport powstaje dopiero po zbudowaniu uwierzytelnienia.
     */
    protected HttpTransport buildEcomTransport() {
        if (authentication != null) {
            return buildTransport(authentication);
        }

        Validate.requireText(username, "username");
        Validate.requireNonNull(password, "password");

        AtomicReference<HttpTransport> loginTransport = new AtomicReference<>();
        EcomJwtAuthentication jwtAuthentication =
                new EcomJwtAuthentication(loginTransport::get, username, password, tokenRefreshSkew);

        HttpTransport transport = buildTransport(jwtAuthentication);
        loginTransport.set(transport.withoutAuthentication());
        return transport;
    }

    /** @throws LoyaltyClubValidationException gdy nie podano zadnych poswiadczen */
    protected void requireCredentials() {
        if (authentication == null && username == null) {
            throw new LoyaltyClubValidationException(
                    "Brak poswiadczen — uzyj credentials(...), basicAuth(...), bearerToken(...) albo authentication(...)");
        }
    }
}
