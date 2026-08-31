package pl.pietruszynski.loyaltyclub.sdk.ecom.auth;

import pl.pietruszynski.loyaltyclub.sdk.core.auth.JwtLoginAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Sesja JWT sklepu internetowego na {@code /api/ecom/auth/**}.
 *
 * <p>Do niedawna ta przestrzen uwierzytelniala sie wylacznie metoda HTTP Basic, przez co
 * SDK przesylalo haslo przy kazdym zadaniu. Backend wystawia teraz komplet punktow
 * logowania, wiec domyslna droga jest token — haslo idzie po sieci raz, przy logowaniu.
 */
public class EcomJwtAuthentication extends JwtLoginAuthentication {

    public static final String BASE_PATH = "/api/ecom/auth";
    public static final String LOGIN_PATH = BASE_PATH + "/login";

    /**
     * @param transportSupplier zrodlo transportu bez uwierzytelniania; leniwe, bo transport
     *                          powstaje dopiero razem z klientem, ktory to uwierzytelnienie trzyma
     * @param refreshSkew       zapas czasu przed wygasnieciem tokenu; {@code null} oznacza 60 s
     */
    public EcomJwtAuthentication(Supplier<HttpTransport> transportSupplier,
                                 String username,
                                 String password,
                                 Duration refreshSkew) {
        super(transportSupplier, BASE_PATH, username, password, refreshSkew);
    }
}
