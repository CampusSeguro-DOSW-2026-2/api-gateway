package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** CA-GW-01-01, CA-GW-01-02, RN-GW-05 e INT-GW-01 (parte de enrutamiento). */
class RoutingIntegrationTest extends GatewayIntegrationTest {

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

    @Test
    void shouldForwardLoginToMs01WithMethodPathQueryAndBodyUnchanged() {
        String body = "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"contrasena\":\"Campus#2026\"}";
        MS01.respond(200, "{\"token\":\"abc\",\"tipo\":\"Bearer\"}");

        client().post().uri("/api/v1/auth/login?origen=web&x=1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"token\":\"abc\",\"tipo\":\"Bearer\"}");

        assertThat(MS01.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.uri()).isEqualTo("/api/v1/auth/login?origen=web&x=1");
            assertThat(request.body()).isEqualTo(body);
        });
    }

    @Test
    void shouldForwardRegisterToMs01AndReturnItsStatus() {
        MS01.respond(201, "{\"id\":\"3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42\"}");

        client().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"correo\":\"maria.perez@escuelaing.edu.co\"}")
                .exchange()
                .expectStatus().isCreated();

        assertThat(MS01.requests()).extracting(RecordedRequest::uri).containsExactly("/api/v1/auth/register");
    }

    @Test
    void shouldRouteEveryPublishedMs01Prefix() {
        client().get().uri("/api/v1/users").exchange().expectStatus().isOk();
        client().get().uri("/api/v1/users/3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42").exchange().expectStatus().isOk();
        client().patch().uri("/api/v1/users/status").exchange().expectStatus().isOk();
        client().get().uri("/api/v1/roles").exchange().expectStatus().isOk();

        assertThat(MS01.requests()).extracting(RecordedRequest::uri).containsExactly(
                "/api/v1/users", "/api/v1/users/3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42",
                "/api/v1/users/status", "/api/v1/roles");
    }

    @Test
    void shouldForwardUnusualVerbAndLetTheMsAnswer() {
        String correlationId = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
        MS01.respond(405, "{\"status\":405,\"codigo\":\"METODO_NO_PERMITIDO\",\"mensaje\":\"Método no permitido.\","
                + "\"errores\":[],\"correlation_id\":\"" + correlationId + "\"}");

        client().delete().uri("/api/v1/roles").header("X-Correlation-ID", correlationId)
                .exchange().expectStatus().isEqualTo(405);

        assertThat(MS01.requests()).extracting(RecordedRequest::method).containsExactly("DELETE");
    }

    @Test
    void shouldReturn404WithoutCallingAnyMsWhenRouteIsUnknown() {
        String body = client().get().uri("/api/v1/inexistente")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 404, "NO_ENCONTRADO", "Ruta no encontrada.");
        assertThat(MS01.requests()).isEmpty();
    }

    @Test
    void shouldNotRouteOutsideThePublishedPrefixes() {
        client().get().uri("/api/v1/rolesx").exchange().expectStatus().isNotFound();
        client().get().uri("/users").exchange().expectStatus().isNotFound();

        assertThat(MS01.requests()).isEmpty();
    }
}
