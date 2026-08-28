package pl.pietruszynski.loyaltyclub.sdk.core.auth;

import lombok.Getter;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Baza dla uwierzytelnienia tokenem, ktory SDK samo pobiera i odswieza.
 *
 * <p>Token trzymany jest w pamieci i wymieniany na nowy, zanim wygasnie — z zapasem
 * {@code refreshSkew}, ktory pochlania zegar rozjechany miedzy klientem a serwerem oraz
 * czas przelotu zadania. Klasa jest bezpieczna watkowo: rownolegle watki czekaja
 * na jedno logowanie zamiast wysylac ich kilka.
 */
public abstract class RefreshingTokenAuthentication implements AuthenticationProvider {

    /** Token wraz z momentem wygasniecia zwroconym przez backend. */
    public record Token(String value, Instant expiresAt) {

        public Token {
            Validate.requireText(value, "token");
            Validate.requireNonNull(expiresAt, "expiresAt");
        }
    }

    public static final Duration DEFAULT_REFRESH_SKEW = Duration.ofSeconds(60);

    private final ReentrantLock lock = new ReentrantLock();

    @Getter
    private final Duration refreshSkew;

    private volatile Token token;

    protected RefreshingTokenAuthentication(Duration refreshSkew) {
        this.refreshSkew = refreshSkew == null ? DEFAULT_REFRESH_SKEW : refreshSkew;
    }

    /** Pobiera swiezy token z backendu. Wolane pod blokada, wiec bez rownoleglych logowan. */
    protected abstract Token fetchToken();

    @Override
    public void authorize(HttpRequest.Builder requestBuilder) {
        requestBuilder.header("Authorization", "Bearer " + currentToken().value());
    }

    @Override
    public boolean refreshAfterUnauthorized() {
        invalidate();
        return true;
    }

    /** Zwraca wazny token, logujac sie lub odswiezajac go w razie potrzeby. */
    public Token currentToken() {
        Token cached = token;
        if (isUsable(cached)) {
            return cached;
        }
        lock.lock();
        try {
            // Inny watek mogl odswiezyc token, zanim ten przejal blokade.
            if (isUsable(token)) {
                return token;
            }
            Token fresh = fetchToken();
            token = fresh;
            return fresh;
        } finally {
            lock.unlock();
        }
    }

    /** Wyrzuca zbuforowany token; nastepne zadanie zaloguje sie ponownie. */
    public void invalidate() {
        lock.lock();
        try {
            token = null;
        } finally {
            lock.unlock();
        }
    }

    private boolean isUsable(Token candidate) {
        return candidate != null && Instant.now().plus(refreshSkew).isBefore(candidate.expiresAt());
    }
}
