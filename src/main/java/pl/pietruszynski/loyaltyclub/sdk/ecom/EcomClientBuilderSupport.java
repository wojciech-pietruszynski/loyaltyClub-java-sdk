package pl.pietruszynski.loyaltyclub.sdk.ecom;

import pl.pietruszynski.loyaltyclub.sdk.core.AbstractClientBuilder;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.AuthenticationProvider;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.BasicAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.BearerTokenAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubValidationException;

import java.util.function.Supplier;

/**
 * Poswiadczenia wspolne dla obu klientow e-commerce. Backend nie wystawia endpointu
 * logowania dla roli {@code ECOM}, wiec domyslna droga jest HTTP Basic; token JWT
 * mozna podac, jesli integracja zdobywa go wlasnym kanalem.
 *
 * @param <B> typ konkretnego buildera
 */
public abstract class EcomClientBuilderSupport<B extends EcomClientBuilderSupport<B>>
        extends AbstractClientBuilder<B> {

    private AuthenticationProvider authentication;

    /** HTTP Basic z poswiadczeniami uzytkownika o roli {@code ECOM}. */
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

    protected AuthenticationProvider requireAuthentication() {
        if (authentication == null) {
            throw new LoyaltyClubValidationException(
                    "Brak poswiadczen — uzyj basicAuth(...), bearerToken(...) albo authentication(...)");
        }
        return authentication;
    }
}
