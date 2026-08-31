package pl.pietruszynski.loyaltyclub.sdk.core.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubApiException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubException;
import pl.pietruszynski.loyaltyclub.sdk.core.http.ApiRequest;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpMethod;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.model.LoginRequest;
import pl.pietruszynski.loyaltyclub.sdk.core.model.LoginResponse;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Sesja JWT prowadzona przez SDK na przestrzeni {@code /api/{kanal}/auth/**}.
 *
 * <p>Backend wystawia dla kazdego kanalu ten sam komplet punktow: {@code /login},
 * {@code /refresh} i {@code /logout}. SDK korzysta z wszystkich trzech:
 * <ul>
 *   <li>pierwsze zadanie loguje sie haslem;</li>
 *   <li>token blisko wygasniecia jest wymieniany przez {@code /refresh} — na tym polega
 *       sens tego punktu: sesja przedluza sie bez ponownego wysylania hasla;</li>
 *   <li>gdy odswiezenie sie nie uda (token zdazyl wygasnac albo zostal wycofany),
 *       nastepuje ciche ponowne logowanie;</li>
 *   <li>{@link #logout()} wycofuje token po stronie backendu — bez tego pozostalby wazny
 *       az do konca swojego czasu zycia, mimo ze integracja go juz nie uzywa.</li>
 * </ul>
 *
 * <p>Klasa jest bezpieczna watkowo; logowanie i odswiezanie ida pod blokada z klasy bazowej.
 */
public class JwtLoginAuthentication extends RefreshingTokenAuthentication {

    private static final TypeReference<LoginResponse> LOGIN_RESPONSE = new TypeReference<>() {
    };

    private final Supplier<HttpTransport> transportSupplier;
    private final String basePath;
    private final String username;
    private final String password;

    /**
     * @param transportSupplier zrodlo transportu bez uwierzytelniania; leniwe, bo transport
     *                          powstaje dopiero razem z klientem, ktory to uwierzytelnienie trzyma
     * @param basePath          przestrzen logowania, np. {@code /api/store/auth}
     * @param refreshSkew       zapas czasu przed wygasnieciem tokenu; {@code null} oznacza 60 s
     */
    public JwtLoginAuthentication(Supplier<HttpTransport> transportSupplier,
                                  String basePath,
                                  String username,
                                  String password,
                                  Duration refreshSkew) {
        super(refreshSkew);
        this.transportSupplier = Validate.requireNonNull(transportSupplier, "transportSupplier");
        this.basePath = Validate.requireText(basePath, "basePath");
        this.username = Validate.requireText(username, "username");
        this.password = Validate.requireNonNull(password, "password");
    }

    /** {@code POST {basePath}/login} */
    public String loginPath() {
        return basePath + "/login";
    }

    /** {@code POST {basePath}/refresh} */
    public String refreshPath() {
        return basePath + "/refresh";
    }

    /** {@code POST {basePath}/logout} */
    public String logoutPath() {
        return basePath + "/logout";
    }

    @Override
    protected Token fetchToken() {
        Token current = cachedToken();
        if (current != null) {
            Token refreshed = tryRefresh(current);
            if (refreshed != null) {
                return refreshed;
            }
        }
        return login();
    }

    /**
     * Wycofuje biezacy token przez {@code POST {basePath}/logout} i czysci go z pamieci.
     *
     * <p>Wylogowanie jest idempotentne po stronie backendu, a brak sesji nie jest bledem —
     * wywolanie bez tokenu po prostu nic nie robi. Kolejne zadanie zaloguje sie od nowa.
     */
    public void logout() {
        Token current = cachedToken();
        invalidate();
        if (current == null) {
            return;
        }
        transport().execute(ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(logoutPath())
                .header("Authorization", "Bearer " + current.value())
                // Wycofanie tokenu jest idempotentne, wiec ponowienie po bledzie sieci jest bezpieczne.
                .retryable(true)
                .build());
    }

    private Token login() {
        return toToken(transport().execute(ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(loginPath())
                .body(LoginRequest.builder().username(username).password(password).build())
                // Logowanie nie zmienia stanu biznesowego, wiec ponowienie jest bezpieczne.
                .retryable(true)
                .build(), LOGIN_RESPONSE), loginPath());
    }

    /**
     * Probuje przedluzyc sesje okazanym tokenem. Zwraca {@code null}, gdy backend token
     * odrzucil — token wygasl miedzy sprawdzeniem a zadaniem albo zostal wycofany.
     * Wolajacy loguje sie wtedy haslem, zamiast wywracac cale zadanie.
     */
    private Token tryRefresh(Token current) {
        try {
            return toToken(transport().execute(ApiRequest.builder()
                    .method(HttpMethod.POST)
                    .path(refreshPath())
                    .header("Authorization", "Bearer " + current.value())
                    .retryable(true)
                    .build(), LOGIN_RESPONSE), refreshPath());
        } catch (LoyaltyClubApiException e) {
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                return null;
            }
            throw e;
        }
    }

    private Token toToken(LoginResponse response, String path) {
        if (response == null || response.getToken() == null || response.getToken().isBlank()) {
            throw new LoyaltyClubException("Wywolanie " + path + " nie zwrocilo tokenu");
        }
        return new Token(response.getToken(), response.expiresAtInstant());
    }

    private HttpTransport transport() {
        HttpTransport transport = transportSupplier.get();
        if (transport == null) {
            throw new LoyaltyClubException("Transport nie jest jeszcze gotowy — klient nie zostal w pelni zbudowany");
        }
        return transport;
    }
}
