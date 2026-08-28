package pl.pietruszynski.loyaltyclub.sdk.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.AuthenticationProvider;
import pl.pietruszynski.loyaltyclub.sdk.core.http.HttpTransport;
import pl.pietruszynski.loyaltyclub.sdk.core.retry.RetryPolicy;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wspolne pokretla konfiguracyjne buildera klienta: adres backendu, pula polaczen,
 * limity czasu, polityka ponowien i naglowki domyslne. Podklasy dokladaja wylacznie
 * to, co specyficzne dla swojego API (poswiadczenia, domyslny kod kraju).
 *
 * @param <B> typ konkretnego buildera, zeby metody lancuchowe zwracaly wlasciwy typ
 */
public abstract class AbstractClientBuilder<B extends AbstractClientBuilder<B>> {

    private String baseUrl;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private Duration connectTimeout;
    private Duration requestTimeout;
    private RetryPolicy retryPolicy;
    private String userAgent;
    private final Map<String, String> defaultHeaders = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    protected B self() {
        return (B) this;
    }

    /** Adres bazowy backendu, np. {@code https://loyalty.example.com} lub {@code http://localhost:8089}. */
    public B baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return self();
    }

    /** Wlasna pula polaczen; przydatne, gdy aplikacja hosta ma juz skonfigurowany {@link HttpClient}. */
    public B httpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        return self();
    }

    /** Wlasny mapper JSON. Musi obslugiwac {@code java.time} zgodnie z kontraktem backendu. */
    public B objectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        return self();
    }

    /** Limit czasu na nawiazanie polaczenia TCP; domyslnie 10 s. */
    public B connectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
        return self();
    }

    /** Limit czasu na cale wywolanie; domyslnie 30 s. */
    public B requestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        return self();
    }

    /** Polityka ponowien; domyslnie 3 proby z wykladniczym backoffem. */
    public B retryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
        return self();
    }

    /** Naglowek doklejany do kazdego zadania, np. identyfikator systemu wywolujacego. */
    public B defaultHeader(String name, String value) {
        this.defaultHeaders.put(name, value);
        return self();
    }

    public B userAgent(String userAgent) {
        this.userAgent = userAgent;
        return self();
    }

    /** Sklada transport z zebranej konfiguracji i podanego zrodla poswiadczen. */
    protected HttpTransport buildTransport(AuthenticationProvider authentication) {
        return HttpTransport.builder()
                .baseUri(Validate.requireText(baseUrl, "baseUrl"))
                .httpClient(httpClient)
                .objectMapper(objectMapper)
                .connectTimeout(connectTimeout)
                .requestTimeout(requestTimeout)
                .retryPolicy(retryPolicy)
                .authentication(authentication)
                .defaultHeaders(defaultHeaders)
                .userAgent(userAgent)
                .build();
    }
}
