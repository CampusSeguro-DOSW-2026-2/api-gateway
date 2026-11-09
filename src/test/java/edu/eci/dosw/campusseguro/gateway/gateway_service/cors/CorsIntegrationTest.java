package edu.eci.dosw.campusseguro.gateway.gateway_service.cors;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** CA-GW-04-01 a CA-GW-04-03 e INT-GW-15 (origen permitido en el perfil test: https://app.campusseguro.test). */
class CorsIntegrationTest extends GatewayIntegrationTest {

    private static final String ALLOWED_ORIGIN = "https://app.campusseguro.test";
    private static final String OTHER_ORIGIN = "https://sitio-malicioso.test";
    private static final DownstreamStub MS01 = DownstreamStub.start();

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

    private WebTestClient.ResponseSpec preflight(String path, String origin, String method, String headers) {
        return anonymousClient().options().uri(path)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, headers)
                .exchange();
    }

    @Test
    void shouldAnswerAllowedPreflightWithoutJwtAndWithoutCallingTheMs() {
        preflight("/api/v1/users/me", ALLOWED_ORIGIN, "GET", "authorization,x-correlation-id")
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods -> assertThat(methods).contains("GET"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        headers -> assertThat(headers.toLowerCase()).contains("authorization", "x-correlation-id"))
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600")
                .expectHeader().value(HttpHeaders.VARY, vary -> assertThat(vary).contains("Origin"))
                .expectBody().isEmpty();

        assertThat(MS01.requests()).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @CsvSource(delimiter = '|', value = {
        OTHER_ORIGIN + "   | GET   | authorization",
        ALLOWED_ORIGIN + " | TRACE | authorization",
        ALLOWED_ORIGIN + " | GET   | x-user-role",
        ALLOWED_ORIGIN + " | POST  | x-service-key"
    })
    void shouldRejectPreflightNotAllowedWith403AndWithoutAllowOrigin(String origin, String method, String headers) {
        String body = preflight("/api/v1/users/me", origin, method, headers)
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 403, "PROHIBIDO", "Acceso denegado por la política perimetral.");
        assertThat(MS01.requests()).isEmpty();
    }

    @Test
    void shouldReturn404ForPreflightToUnknownRoute() {
        String body = preflight("/api/v1/inexistente", ALLOWED_ORIGIN, "GET", "authorization")
                .expectStatus().isNotFound()
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 404, "NO_ENCONTRADO", "Ruta no encontrada.");
    }

    @Test
    void shouldExposeTheCorrelationIdOnAnAllowedRealRequest() {
        client().get().uri("/api/v1/users/me").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        exposed -> assertThat(exposed).contains("X-Correlation-ID"));
    }

    @Test
    void shouldProcessRealRequestFromOtherOriginWithoutCorsHeaders() {
        client().get().uri("/api/v1/users/me").header(HttpHeaders.ORIGIN, OTHER_ORIGIN)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);

        assertThat(MS01.requests()).hasSize(1);
    }

    @Test
    void shouldProcessRequestWithoutOriginWithoutCorsHeaders() {
        client().method(HttpMethod.GET).uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
    }

    @Test
    void shouldStillRequireJwtOnRealRequestsFromAnAllowedOrigin() {
        anonymousClient().get().uri("/api/v1/users/me").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(MS01.requests()).isEmpty();
    }
}
