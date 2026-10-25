package edu.eci.dosw.campusseguro.gateway.gateway_service.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/** Doble HTTP de un MS: registra cada solicitud recibida y responde lo configurado. */
public final class DownstreamStub implements AutoCloseable {

    public record RecordedRequest(String method, String uri, Map<String, List<String>> headers, String body) {

        /** Valores de una cabecera, sin distinguir mayúsculas/minúsculas. */
        public List<String> header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .toList();
        }
    }

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile String responseBody = "{\"mensaje\":\"ok\"}";
    private volatile Duration delay = Duration.ZERO;
    private final Map<String, String> responseHeaders = new ConcurrentHashMap<>();

    private DownstreamStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    public static DownstreamStub start() {
        try {
            return new DownstreamStub();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public DownstreamStub respond(int status, String body) {
        this.status = status;
        this.responseBody = body;
        return this;
    }

    public DownstreamStub header(String name, String value) {
        responseHeaders.put(name, value);
        return this;
    }

    public DownstreamStub delay(Duration delay) {
        this.delay = delay;
        return this;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        status = 200;
        responseBody = "{\"mensaje\":\"ok\"}";
        delay = Duration.ZERO;
        responseHeaders.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                Map.copyOf(exchange.getRequestHeaders()), body));
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        responseHeaders.forEach(exchange.getResponseHeaders()::add);
        try (OutputStream out = exchange.getResponseBody()) {
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            out.write(bytes);
        } catch (IOException ignored) {
            // El GW ya cortó la conexión (p. ej. por timeout).
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
