package pl.pietruszynski.loyaltyclub.sdk.core.auth;

import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;

import java.net.http.HttpRequest;
import java.util.function.Supplier;

/**
 * Token JWT dostarczany z zewnatrz — dla integracji, ktore zdobywaja token wlasnym
 * kanalem i tylko chca, zeby SDK go doklejal.
 */
public final class BearerTokenAuthentication implements AuthenticationProvider {

    private final Supplier<String> tokenSupplier;

    public BearerTokenAuthentication(String token) {
        Validate.requireText(token, "token");
        this.tokenSupplier = () -> token;
    }

    public BearerTokenAuthentication(Supplier<String> tokenSupplier) {
        this.tokenSupplier = Validate.requireNonNull(tokenSupplier, "tokenSupplier");
    }

    @Override
    public void authorize(HttpRequest.Builder requestBuilder) {
        requestBuilder.header("Authorization", "Bearer " + Validate.requireText(tokenSupplier.get(), "token"));
    }
}
