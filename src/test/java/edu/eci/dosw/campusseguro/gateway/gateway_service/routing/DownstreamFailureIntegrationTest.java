package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** CA-GW-01-03, CA-GW-01-04, RN-GW-04, INT-GW-09 e INT-GW-10. */
class DownstreamFailureIntegrationTest extends GatewayIntegrationTest {

    private static final DownstreamStub SLOW_MS = DownstreamStub.start().delay(Duration.ofSeconds(2));

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("gateway.downstream-timeout", () -> "300ms");
        registry.add("gateway.routes[0].id", () -> "slow");
        registry.add("gateway.routes[0].uri", SLOW_MS::baseUrl);
        registry.add("gateway.routes[0].paths[0]", () -> "/api/v1/slow/**");
        registry.add("gateway.routes[1].id", () -> "closed");
        registry.add("gateway.routes[1].uri", () -> "http://localhost:" + closedPort());
        registry.add("gateway.routes[1].paths[0]", () -> "/api/v1/closed/**");
        registry.add("gateway.routes[2].id", () -> "unknown-host");
        registry.add("gateway.routes[2].uri", () -> "http://ms-inexistente.invalid:8089");
        registry.add("gateway.routes[2].paths[0]", () -> "/api/v1/unknown-host/**");
    }

    @AfterAll
    static void stop() {
        SLOW_MS.close();
    }

    @Test
    void shouldReturn504WithoutRetryingWhenDestinationIsSlow() {
        String body = client().get().uri("/api/v1/slow/recurso")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 504, "TIEMPO_AGOTADO", "El servicio de destino no respondió a tiempo.");
        assertThat(SLOW_MS.requests()).hasSize(1);
    }

    @Test
    void shouldReturn502WithoutInternalDetailsWhenPortIsClosed() {
        String body = client().get().uri("/api/v1/closed/recurso")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 502, "SERVICIO_NO_DISPONIBLE", "Servicio temporalmente no disponible.");
    }

    @Test
    void shouldReturn502WithoutInternalDetailsWhenHostIsUnknown() {
        String body = client().post().uri("/api/v1/unknown-host/recurso")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 502, "SERVICIO_NO_DISPONIBLE", "Servicio temporalmente no disponible.");
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
