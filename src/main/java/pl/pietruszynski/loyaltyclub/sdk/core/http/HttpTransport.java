package pl.pietruszynski.loyaltyclub.sdk.core.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.AuthenticationProvider;
import pl.pietruszynski.loyaltyclub.sdk.core.auth.NoAuthentication;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.BadRequestException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.ForbiddenException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubApiException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubSerializationException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubTransportException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.NotFoundException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.ServerException;
import pl.pietruszynski.loyaltyclub.sdk.core.exception.UnauthorizedException;
import pl.pietruszynski.loyaltyclub.sdk.core.json.LoyaltyClubJson;
import pl.pietruszynski.loyaltyclub.sdk.core.model.ProblemDetail;
import pl.pietruszynski.loyaltyclub.sdk.core.retry.RetryPolicy;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Warstwa transportowa SDK: budowa zadania, serializacja JSON, uwierzytelnianie,
 * ponowienia i tlumaczenie odpowiedzi bledow na wyjatki.
 *
 * <p>Instancja jest bezpieczna watkowo i przeznaczona do wspoldzielenia — opakowany
 * {@link HttpClient} utrzymuje pule polaczen, wiec tworzenie transportu na kazde zadanie
 * niweczyloby keep-alive.
 */
public class HttpTransport implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(HttpTransport.class.getName());
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_USER_AGENT = "loyaltyclub-java-sdk/1.0";

    @Getter
    private final URI baseUri;
    private final HttpClient httpClient;
    private final boolean ownsHttpClient;
    @Getter
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;
    @Getter
    private final RetryPolicy retryPolicy;
    private final AuthenticationProvider authentication;
    private final Map<String, String> defaultHeaders;
    private final String userAgent;

    private HttpTransport(Builder builder) {
        this.baseUri = normalizeBaseUri(Validate.requireNonNull(builder.baseUri, "baseUri"));
        this.objectMapper = builder.objectMapper != null ? builder.objectMapper : LoyaltyClubJson.createDefault();
        this.requestTimeout = builder.requestTimeout != null ? builder.requestTimeout : DEFAULT_REQUEST_TIMEOUT;
        this.retryPolicy = builder.retryPolicy != null ? builder.retryPolicy : RetryPolicy.defaultPolicy();
        this.authentication = builder.authentication != null ? builder.authentication : NoAuthentication.INSTANCE;
        this.defaultHeaders = Map.copyOf(builder.defaultHeaders);
        this.userAgent = builder.userAgent != null ? builder.userAgent : DEFAULT_USER_AGENT;
        this.ownsHttpClient = builder.httpClient == null;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(builder.connectTimeout != null ? builder.connectTimeout : DEFAULT_CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Zwraca transport wykonujacy zadania bez uwierzytelniania — uzywany dla wywolania
     * logowania, ktore poswiadczen w naglowku nie potrzebuje. Wspoldzieli pule polaczen
     * i mapper z oryginalem.
     */
    public HttpTransport withoutAuthentication() {
        return builder()
                .baseUri(baseUri)
                .httpClient(httpClient)
                .objectMapper(objectMapper)
                .requestTimeout(requestTimeout)
                .retryPolicy(retryPolicy)
                .authentication(NoAuthentication.INSTANCE)
                .defaultHeaders(defaultHeaders)
                .userAgent(userAgent)
                .build();
    }

    /** Wykonuje zadanie i deserializuje odpowiedz do wskazanego typu. */
    public <T> T execute(ApiRequest request, TypeReference<T> responseType) {
        HttpResponse<String> response = send(request);
        String body = response.body();
        if (responseType == null || body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, responseType);
        } catch (IOException e) {
            throw new LoyaltyClubSerializationException(
                    "Nie udalo sie zdeserializowac odpowiedzi z " + request.getPath(), e);
        }
    }

    /** Wykonuje zadanie i ignoruje cialo odpowiedzi. */
    public void execute(ApiRequest request) {
        send(request);
    }

    private HttpResponse<String> send(ApiRequest request) {
        Validate.requireNonNull(request, "request");
        byte[] payload = serializeBody(request);

        int attempt = 1;
        boolean authRefreshed = false;

        while (true) {
            HttpResponse<String> response;
            try {
                response = httpClient.send(buildHttpRequest(request, payload),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (retryPolicy.isRetryOnIoException() && canRetry(request, attempt)) {
                    sleepBeforeRetry(attempt, request, e.toString());
                    attempt++;
                    continue;
                }
                throw new LoyaltyClubTransportException(
                        "Wywolanie " + request.getMethod() + " " + request.getPath() + " nie powiodlo sie", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LoyaltyClubTransportException(
                        "Watek przerwany podczas wywolania " + request.getMethod() + " " + request.getPath(), e);
            }

            int status = response.statusCode();

            // Wygasly token: odswiez poswiadczenia i sprobuj raz jeszcze, nie zuzywajac proby z puli retry.
            if (status == 401 && !authRefreshed && authentication.refreshAfterUnauthorized()) {
                authRefreshed = true;
                LOGGER.log(System.Logger.Level.DEBUG,
                        () -> "HTTP 401 dla " + request.getPath() + " — odswiezam poswiadczenia i ponawiam");
                continue;
            }

            if (retryPolicy.isRetryableStatus(status) && canRetry(request, attempt)) {
                sleepBeforeRetry(attempt, request, "HTTP " + status);
                attempt++;
                continue;
            }

            if (status >= 200 && status < 300) {
                return response;
            }
            throw toApiException(status, response.body());
        }
    }

    private boolean canRetry(ApiRequest request, int attempt) {
        return request.isRetryable() && attempt < retryPolicy.getMaxAttempts();
    }

    private void sleepBeforeRetry(int attempt, ApiRequest request, String reason) {
        Duration backoff = retryPolicy.backoffBefore(attempt);
        LOGGER.log(System.Logger.Level.DEBUG, () -> "Ponawiam " + request.getMethod() + " " + request.getPath()
                + " (proba " + (attempt + 1) + "/" + retryPolicy.getMaxAttempts() + ") po " + backoff.toMillis()
                + " ms, powod: " + reason);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LoyaltyClubTransportException("Watek przerwany podczas oczekiwania na ponowienie", e);
        }
    }

    private byte[] serializeBody(ApiRequest request) {
        if (request.getBody() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(request.getBody());
        } catch (IOException e) {
            throw new LoyaltyClubSerializationException(
                    "Nie udalo sie zserializowac ciala zadania dla " + request.getPath(), e);
        }
    }

    private HttpRequest buildHttpRequest(ApiRequest request, byte[] payload) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolveUri(request))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent);

        defaultHeaders.forEach(builder::header);
        request.getHeaders().forEach(builder::header);
        authentication.authorize(builder);

        if (payload == null) {
            builder.method(request.getMethod().name(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(request.getMethod().name(), HttpRequest.BodyPublishers.ofByteArray(payload));
        }
        return builder.build();
    }

    private URI resolveUri(ApiRequest request) {
        StringBuilder url = new StringBuilder(baseUri.toString()).append(request.getPath());
        Map<String, String> queryParams = request.getQueryParams();
        if (!queryParams.isEmpty()) {
            StringJoiner query = new StringJoiner("&", "?", "");
            queryParams.forEach((name, value) -> query.add(encode(name) + "=" + encode(value)));
            url.append(query);
        }
        return URI.create(url.toString());
    }

    private static String encode(String value) {
        // URLEncoder koduje spacje jako '+', co w komponencie query bywa dekodowane niejednoznacznie.
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private LoyaltyClubApiException toApiException(int status, String body) {
        ProblemDetail problemDetail = parseProblemDetail(body);
        return switch (status) {
            case 400 -> new BadRequestException(problemDetail, body);
            case 401 -> new UnauthorizedException(problemDetail, body);
            case 403 -> new ForbiddenException(problemDetail, body);
            case 404 -> new NotFoundException(problemDetail, body);
            default -> status >= 500
                    ? new ServerException(status, problemDetail, body)
                    : new LoyaltyClubApiException(status, problemDetail, body);
        };
    }

    private ProblemDetail parseProblemDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, ProblemDetail.class);
        } catch (IOException e) {
            // Backend potrafi odpowiedziec samym kodem statusu (np. 401 z entry pointa bezpieczenstwa)
            // albo strona bledu kontenera — surowe cialo zostaje wtedy w wyjatku.
            LOGGER.log(System.Logger.Level.TRACE, "Cialo bledu nie jest dokumentem ProblemDetail", e);
            return null;
        }
    }

    private static URI normalizeBaseUri(URI baseUri) {
        String value = baseUri.toString();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return URI.create(value);
    }

    /** Zamyka pule polaczen, o ile transport sam ja utworzyl. */
    @Override
    public void close() {
        if (ownsHttpClient) {
            httpClient.close();
        }
    }

    public static final class Builder {

        private URI baseUri;
        private HttpClient httpClient;
        private ObjectMapper objectMapper;
        private Duration connectTimeout;
        private Duration requestTimeout;
        private RetryPolicy retryPolicy;
        private AuthenticationProvider authentication;
        private Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private String userAgent;

        public Builder baseUri(URI baseUri) {
            this.baseUri = baseUri;
            return this;
        }

        public Builder baseUri(String baseUri) {
            this.baseUri = URI.create(Validate.requireText(baseUri, "baseUri"));
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder authentication(AuthenticationProvider authentication) {
            this.authentication = authentication;
            return this;
        }

        public Builder defaultHeaders(Map<String, String> defaultHeaders) {
            this.defaultHeaders = new LinkedHashMap<>(defaultHeaders);
            return this;
        }

        public Builder defaultHeader(String name, String value) {
            this.defaultHeaders.put(name, value);
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public HttpTransport build() {
            return new HttpTransport(this);
        }
    }
}
