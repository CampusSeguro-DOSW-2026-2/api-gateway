package edu.eci.dosw.campusseguro.gateway.gateway_service.support;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Base de las pruebas de integración: GW real en un puerto aleatorio con la llave de {@link TestJwt}.
 * Todas llevan la etiqueta {@code integracion} ({@code mvn test -Dgroups=integracion}). La consulta de
 * cortes de sesión va a {@link #SESSION_CUTS}, un doble aparte del de MS01 de cada prueba, para que no cuente
 * como solicitud a MS01.
 */
@Tag("integracion")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class GatewayIntegrationTest {

    /** Instantánea sin ningún corte de sesión */
    protected static final String NO_CUTS =
            "{\"generado_en\":\"2026-11-10T23:00:00Z\",\"ventana_segundos\":3700,\"usuarios\":[],\"roles\":[]}";

    /** Doble de la ruta interna de cortes de sesión de MS01, compartido por todas las pruebas de integración */
    protected static final DownstreamStub SESSION_CUTS = DownstreamStub.start().respond(200, NO_CUTS);

    @LocalServerPort
    protected int port;

    /**
     * Registra la llave pública con la que firma TestJwt
     */
    @DynamicPropertySource
    static void jwtPublicKey(DynamicPropertyRegistry registry) {
        registry.add("JWT_PUBLIC_KEY", TestJwt::publicKeyPem);
    }

    /**
     * Apunta la consulta de cortes de sesión al doble compartido
     */
    @DynamicPropertySource
    static void sessionCutsUrl(DynamicPropertyRegistry registry) {
        registry.add("SESSION_CUTS_URL", () -> SESSION_CUTS.baseUrl() + "/api/v1/internal/session-cuts");
    }

    /** Deja el doble de cortes de sesión sin cortes para la prueba siguiente */
    @AfterEach
    void restoreSessionCuts() {
        SESSION_CUTS.reset();
        SESSION_CUTS.respond(200, NO_CUTS);
    }

    /** Cliente con un JWT válido, como el frontend tras el login. */
    protected WebTestClient client() {
        return anonymousClient().mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validToken())
                .build();
    }

    protected WebTestClient anonymousClient() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }
}
