package edu.eci.dosw.campusseguro.gateway.gateway_service.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/** CA-GW-06-01 a CA-GW-06-03, RN-GW-16 e INT-GW-08. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CorrelationIntegrationTest {

    private static final String VALID_V4 = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final DownstreamStub MS01 = DownstreamStub.start();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("MS01_URI", MS01::baseUrl);
    }

    @AfterAll
    static void stop() {
        MS01.close();
    }

    @BeforeEach
    void reset() {
        MS01.reset();
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private String receivedByMs01() {
        assertThat(MS01.requests()).hasSize(1);
        return MS01.requests().get(0).header("X-Correlation-ID").stream().reduce((a, b) -> a + "," + b).orElseThrow();
    }

    @Test
    void shouldGenerateV4ThatReachesTheMsAndReturnsWhenHeaderIsMissing() {
        String returned = client().get().uri("/api/v1/roles").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseHeaders().getFirst("X-Correlation-ID");

        assertThat(UUID.fromString(returned).version()).isEqualTo(4);
        assertThat(receivedByMs01()).isEqualTo(returned);
    }

    @Test
    void shouldKeepValidV4EndToEnd() {
        client().get().uri("/api/v1/roles").header("X-Correlation-ID", VALID_V4).exchange()
                .expectHeader().valueEquals("X-Correlation-ID", VALID_V4);

        assertThat(receivedByMs01()).isEqualTo(VALID_V4);
    }

    @Test
    void shouldReplaceInvalidValueAndStillSucceed() {
        String returned = client().get().uri("/api/v1/roles").header("X-Correlation-ID", "no-es-uuid").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseHeaders().getFirst("X-Correlation-ID");

        assertThat(returned).isNotEqualTo("no-es-uuid");
        assertThat(receivedByMs01()).isEqualTo(returned);
    }

    @Test
    void shouldReplaceDuplicatedHeaderWithSingleValue() {
        String returned = client().get().uri("/api/v1/roles")
                .header("X-Correlation-ID", VALID_V4, VALID_V4).exchange()
                .returnResult(String.class).getResponseHeaders().getFirst("X-Correlation-ID");

        assertThat(returned).isNotEqualTo(VALID_V4);
        assertThat(receivedByMs01()).isEqualTo(returned);
    }

    @Test
    void shouldReturnContextHeaderEvenIfTheMsSendsAnother() {
        MS01.header("X-Correlation-ID", "11111111-1111-4111-8111-111111111111");

        client().get().uri("/api/v1/roles").header("X-Correlation-ID", VALID_V4).exchange()
                .expectHeader().valuesMatch("X-Correlation-ID", VALID_V4);
    }

    @Test
    void shouldCarrySameValueInHeaderAndBodyOnGatewayError() {
        EntityExchangeResult<String> result = client().get().uri("/api/v1/inexistente")
                .header("X-Correlation-ID", VALID_V4).exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Correlation-ID", VALID_V4)
                .expectBody(String.class).returnResult();

        assertThat(result.getResponseBody()).contains("\"correlation_id\":\"" + VALID_V4 + "\"");
    }
}
