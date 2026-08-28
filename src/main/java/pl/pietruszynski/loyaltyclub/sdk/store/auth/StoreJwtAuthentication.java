package pl.pietruszynski.loyaltyclub.sdk.store.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.RefreshingTokenAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubException;
import pl.pietruszynski.loyaltyclub.sdk.core.http.ApiRequest;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpMethod;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreLoginRequest;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreLoginResponse;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Uwierzytelnianie sklepu tokenem JWT z {@code POST /api/store/auth/login}.
 *
 * <p>Token backendu zyje 15 minut, wiec SDK loguje sie ponownie samo — zaraz przed
 * uplywem waznosci oraz po odpowiedzi HTTP 401. Wywolujacy nie musi w ogole dotykac tokenu.
 */
public class StoreJwtAuthentication extends RefreshingTokenAuthentication {

    public static final String LOGIN_PATH = "/api/store/auth/login";

    private final Supplier<HttpTransport> transportSupplier;
    private final String username;
    private final String password;

    /**
     * @param transportSupplier zrodlo transportu bez uwierzytelniania; leniwe, bo transport
     *                          powstaje dopiero razem z klientem, ktory to uwierzytelnienie trzyma
     * @param refreshSkew       zapas czasu przed wygasnieciem tokenu; {@code null} oznacza 60 s
     */
    public StoreJwtAuthentication(Supplier<HttpTransport> transportSupplier,
                                  String username,
                                  String password,
                                  Duration refreshSkew) {
        super(refreshSkew);
        this.transportSupplier = Validate.requireNonNull(transportSupplier, "transportSupplier");
        this.username = Validate.requireText(username, "username");
        this.password = Validate.requireNonNull(password, "password");
    }

    @Override
    protected Token fetchToken() {
        HttpTransport transport = transportSupplier.get();
        if (transport == null) {
            throw new LoyaltyClubException("Transport nie jest jeszcze gotowy — klient nie zostal w pelni zbudowany");
        }

        StoreLoginResponse response = transport.execute(
                ApiRequest.builder()
                        .method(HttpMethod.POST)
                        .path(LOGIN_PATH)
                        .body(StoreLoginRequest.builder().username(username).password(password).build())
                        // Logowanie nie zmienia stanu biznesowego, wiec ponowienie jest bezpieczne.
                        .retryable(true)
                        .build(),
                new TypeReference<StoreLoginResponse>() {
                });

        if (response == null || response.getToken() == null || response.getToken().isBlank()) {
            throw new LoyaltyClubException("Logowanie do " + LOGIN_PATH + " nie zwrocilo tokenu");
        }
        return new Token(response.getToken(), response.expiresAtInstant());
    }
}
