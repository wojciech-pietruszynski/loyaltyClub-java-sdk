package pl.pietruszynski.loyaltyclub.sdk.store.auth;

import pl.pietruszynski.loyaltyclub.sdk.core.auth.JwtLoginAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Sesja JWT kasy na {@code /api/store/auth/**}.
 *
 * <p>Token backendu zyje 15 minut, wiec SDK przedluza sesje samo — przez {@code /refresh}
 * tuz przed uplywem waznosci, a po odpowiedzi HTTP 401 przez ponowne logowanie.
 * Wywolujacy nie musi w ogole dotykac tokenu.
 */
public class StoreJwtAuthentication extends JwtLoginAuthentication {

    public static final String BASE_PATH = "/api/store/auth";
    public static final String LOGIN_PATH = BASE_PATH + "/login";

    /**
     * @param transportSupplier zrodlo transportu bez uwierzytelniania; leniwe, bo transport
     *                          powstaje dopiero razem z klientem, ktory to uwierzytelnienie trzyma
     * @param refreshSkew       zapas czasu przed wygasnieciem tokenu; {@code null} oznacza 60 s
     */
    public StoreJwtAuthentication(Supplier<HttpTransport> transportSupplier,
                                  String username,
                                  String password,
                                  Duration refreshSkew) {
        super(transportSupplier, BASE_PATH, username, password, refreshSkew);
    }
}
