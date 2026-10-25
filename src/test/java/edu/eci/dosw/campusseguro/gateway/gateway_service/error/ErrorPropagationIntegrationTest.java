package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** INT-GW-06, INT-GW-07 y CA-GW-05-02 a CA-GW-05-04. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorPropagationIntegrationTest {

    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
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

    private static String errorBody(int status, String codigo, String correlationId) {
        return "{\"status\":" + status + ",\"codigo\":\"" + codigo + "\",\"mensaje\":\"Mensaje del MS.\","
                + "\"errores\":[],\"correlation_id\":\"" + correlationId + "\"}";
    }

    private WebTestClient.ResponseSpec post() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .post().uri("/api/v1/auth/register")
                .header("X-Correlation-ID", CORRELATION_ID)
                .exchange();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"400, VALIDACION", "401, NO_AUTENTICADO", "403, PROHIBIDO", "404, NO_ENCONTRADO",
        "405, METODO_NO_PERMITIDO", "409, CONFLICTO", "422, REGLA_NEGOCIO", "500, ERROR_INTERNO"})
    void shouldPropagateConformingMsErrorUnchanged(int status, String codigo) {
        String body = errorBody(status, codigo, CORRELATION_ID);
        MS01.respond(status, body);

        post().expectStatus().isEqualTo(status)
                .expectHeader().valueEquals("X-Correlation-ID", CORRELATION_ID)
                .expectBody(String.class).isEqualTo(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"status\":409,\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":[]}",
        "{\"status\":409,\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":[],\"correlation_id\":\"11111111-1111-4111-8111-111111111111\"}",
        "{\"timestamp\":\"2026-10-25T10:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"path\":\"/api/v1/auth/register\"}",
        "<html><body>Whitelabel Error Page</body></html>"
    })
    void shouldReplaceNonConformingMsErrorWith502(String body) {
        MS01.respond(409, body);

        String response = post().expectStatus().isEqualTo(502)
                .expectHeader().valueEquals("X-Correlation-ID", CORRELATION_ID)
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(response, 502, "SERVICIO_NO_DISPONIBLE", "Servicio temporalmente no disponible.");
    }

    @Test
    void shouldReplaceSpringDefault405With502() {
        MS01.respond(405, "{\"timestamp\":\"2026-10-25T10:00:00Z\",\"status\":405,\"error\":\"Method Not Allowed\","
                + "\"path\":\"/api/v1/auth/register\"}");

        post().expectStatus().isEqualTo(502);
    }

    @Test
    void shouldReplaceErrorWhoseStatusDiffersFromTheHttpCodeWith502() {
        MS01.respond(409, errorBody(400, "VALIDACION", CORRELATION_ID));

        post().expectStatus().isEqualTo(502);
    }
}
