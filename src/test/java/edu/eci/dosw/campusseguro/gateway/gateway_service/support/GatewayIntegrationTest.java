package edu.eci.dosw.campusseguro.gateway.gateway_service.support;

import java.time.Duration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Base de las pruebas de integración: GW real en un puerto aleatorio con la llave de {@link TestJwt}. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class GatewayIntegrationTest {

    @LocalServerPort
    protected int port;

    @DynamicPropertySource
    static void jwtPublicKey(DynamicPropertyRegistry registry) {
        registry.add("JWT_PUBLIC_KEY", TestJwt::publicKeyPem);
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
