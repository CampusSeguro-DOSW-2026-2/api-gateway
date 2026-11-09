package edu.eci.dosw.campusseguro.gateway.gateway_service.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * Doble HTTPS de un MS detrás de su Caddy (ADR-006): exige un certificado de cliente firmado por la CA indicada y
 * registra el sujeto del certificado de cada solicitud.
 */
public final class MtlsStub implements AutoCloseable {

    /** Contraseña de los almacenes de servidor de src/test/resources/mtls (solo pruebas). */
    public static final String SERVER_STORE_PASSWORD = "servidor-de-prueba";

    private final HttpsServer server;
    private final List<String> clientSubjects = new CopyOnWriteArrayList<>();

    private MtlsStub(String serverStore, String trustedCa) throws IOException, GeneralSecurityException {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers(serverStore), trustManagers(trustedCa), null);
        server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
                // En JDK 21, HttpsParameters.setNeedClientAuth no llega al handshake: se aplica con SSLParameters.
                SSLParameters sslParameters = getSSLContext().getDefaultSSLParameters();
                sslParameters.setNeedClientAuth(true);
                params.setSSLParameters(sslParameters);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * @param serverStore PKCS12 del classpath con el certificado de servidor
     * @param trustedCa   PEM del classpath con la CA que debe firmar el certificado del cliente
     */
    public static MtlsStub start(String serverStore, String trustedCa) {
        try {
            return new MtlsStub(serverStore, trustedCa);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String baseUrl() {
        return "https://localhost:" + server.getAddress().getPort();
    }

    /** Sujeto del certificado de cliente de cada solicitud que completó el handshake. */
    public List<String> clientSubjects() {
        return List.copyOf(clientSubjects);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            clientSubjects.add(((HttpsExchange) exchange).getSSLSession().getPeerPrincipal().getName());
        } catch (SSLPeerUnverifiedException e) {
            clientSubjects.add("sin certificado");
        }
        byte[] body = "{\"mensaje\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static javax.net.ssl.KeyManager[] keyManagers(String serverStore)
            throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = new ClassPathResource(serverStore).getInputStream()) {
            store.load(in, SERVER_STORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store, SERVER_STORE_PASSWORD.toCharArray());
        return factory.getKeyManagers();
    }

    private static javax.net.ssl.TrustManager[] trustManagers(String trustedCa)
            throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        try (InputStream in = new ClassPathResource(trustedCa).getInputStream()) {
            store.setCertificateEntry("ca", CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        return factory.getTrustManagers();
    }
}
