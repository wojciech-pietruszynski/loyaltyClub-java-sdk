package pl.pietruszynski.loyaltyclub.sdk.core.auth;

import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP Basic — sposob uwierzytelnienia zalecany dla integracji e-commerce
 * ({@code /api/ecom/**} i {@code /api/coupon/**}), bo backend nie wystawia
 * endpointu logowania dla roli {@code ECOM}.
 */
public final class BasicAuthentication implements AuthenticationProvider {

    private final String headerValue;

    public BasicAuthentication(String username, String password) {
        Validate.requireText(username, "username");
        Validate.requireNonNull(password, "password");
        String credentials = username + ":" + password;
        this.headerValue = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void authorize(HttpRequest.Builder requestBuilder) {
        requestBuilder.header("Authorization", headerValue);
    }
}
