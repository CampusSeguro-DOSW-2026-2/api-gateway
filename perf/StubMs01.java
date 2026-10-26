import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Doble de MS01 con latencia fija para la prueba de carga (RNF-GW-01). Responde 200 a cualquier ruta.
 * Uso: java perf/StubMs01.java [puerto=8081] [latencia_ms=20]
 */
public class StubMs01 {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8081;
        long latencyMs = args.length > 1 ? Long.parseLong(args[1]) : 20;
        byte[] body = "{\"mensaje\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        System.out.printf("Doble de MS01 en el puerto %d con latencia de %d ms%n", port, latencyMs);
    }
}
