package edu.eci.dosw.campusseguro.gateway.gateway_service.health;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/** CA-GW-07-03, CA-GW-07-04 e INT-GW-14. En el perfil test MS01 apunta a un puerto sin servicio (A3). */
class HealthIntegrationTest extends GatewayIntegrationTest {

    @LocalManagementPort
    private int managementPort;

    private WebTestClient managementClient() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();
    }

    @Test
    void shouldAnswerUpWithoutJwtNorDetailsWithin200MsEvenIfMs01IsDown() {
        anonymousClient().get().uri("/actuator/health").exchange();

        long start = System.nanoTime();
        anonymousClient().get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"status\":\"UP\"}", org.springframework.test.json.JsonCompareMode.STRICT);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThanOrEqualTo(Duration.ofMillis(200));
    }

    @Test
    void shouldNotExposeTheRestOfActuatorOnTheExternalPort() {
        String body = anonymousClient().get().uri("/actuator/metrics").exchange()
                .expectStatus().isNotFound()
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 404, "NO_ENCONTRADO", "Ruta no encontrada.");
    }

    @Test
    void shouldExposeHealthAndMetricsOnTheManagementPort() {
        managementClient().get().uri("/actuator/metrics").exchange().expectStatus().isOk();
        managementClient().get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
