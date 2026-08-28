package pl.pietruszynski.loyaltyclub.sdk.core.auth;

import java.net.http.HttpRequest;

/**
 * Brak uwierzytelnienia — uzywane wewnetrznie dla wywolania logowania,
 * ktore samo poswiadczen w naglowku nie potrzebuje.
 */
public final class NoAuthentication implements AuthenticationProvider {

    public static final NoAuthentication INSTANCE = new NoAuthentication();

    private NoAuthentication() {
    }

    @Override
    public void authorize(HttpRequest.Builder requestBuilder) {
        // celowo pusto
    }
}
