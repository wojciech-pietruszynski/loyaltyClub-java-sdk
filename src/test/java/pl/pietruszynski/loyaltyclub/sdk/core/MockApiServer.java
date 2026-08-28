package pl.pietruszynski.loyaltyclub.sdk.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Serwer-atrapa oparty na {@code com.sun.net.httpserver} z JDK — pozwala testowac
 * cala warstwe transportowa bez dodatkowej zaleznosci testowej.
 *
 * <p>Odpowiedzi sa kolejkowane w kolejnosci, w jakiej maja zostac zwrocone; kazde
 * przychodzace zadanie jest zapisywane i mozna je odczytac przez {@link #takeRequest()}.
 */
public class MockApiServer implements AutoCloseable {

    /** Zapis pojedynczego zadania, ktore trafilo na serwer. */
    public record RecordedRequest(String method,
                                  String path,
                                  String query,
                                  Map<String, List<String>> headers,
                                  String body) {

        public String header(String name) {
            List<String> values = headers.get(name.toLowerCase());
            return values == null || values.isEmpty() ? null : values.getFirst();
        }
    }

    private record QueuedResponse(int status, String contentType, String body) {
    }

    private final HttpServer server;
    private final ConcurrentLinkedQueue<QueuedResponse> responses = new ConcurrentLinkedQueue<>();
    private final LinkedBlockingQueue<RecordedRequest> requests = new LinkedBlockingQueue<>();

    private MockApiServer(HttpServer server) {
        this.server = server;
    }

    public static MockApiServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MockApiServer mock = new MockApiServer(server);
            server.createContext("/", mock::handle);
            server.start();
            return mock;
        } catch (IOException e) {
            throw new UncheckedIOException("Nie udalo sie wystartowac serwera-atrapy", e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Kolejkuje odpowiedz JSON. */
    public MockApiServer enqueueJson(int status, String body) {
        responses.add(new QueuedResponse(status, "application/json", body));
        return this;
    }

    /** Kolejkuje odpowiedz RFC 7807. */
    public MockApiServer enqueueProblem(int status, String body) {
        responses.add(new QueuedResponse(status, "application/problem+json", body));
        return this;
    }

    /** Kolejkuje odpowiedz bez ciala. */
    public MockApiServer enqueueEmpty(int status) {
        responses.add(new QueuedResponse(status, null, ""));
        return this;
    }

    /** Zdejmuje najstarsze zapisane zadanie; czeka az do timeoutu, jesli jeszcze nie doszlo. */
    public RecordedRequest takeRequest() {
        try {
            RecordedRequest request = requests.poll(5, TimeUnit.SECONDS);
            if (request == null) {
                throw new IllegalStateException("Zadne zadanie nie dotarlo do serwera-atrapy");
            }
            return request;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Przerwano oczekiwanie na zadanie", e);
        }
    }

    /** Liczba zadan, ktore dotarly na serwer. */
    public int receivedRequestCount() {
        return requests.size();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    entry -> entry.getKey().toLowerCase(),
                                    Map.Entry::getValue)),
                    body));

            QueuedResponse response = responses.poll();
            if (response == null) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }

            byte[] payload = response.body() == null
                    ? new byte[0]
                    : response.body().getBytes(StandardCharsets.UTF_8);
            if (response.contentType() != null) {
                exchange.getResponseHeaders().add("Content-Type", response.contentType());
            }
            exchange.sendResponseHeaders(response.status(), payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
