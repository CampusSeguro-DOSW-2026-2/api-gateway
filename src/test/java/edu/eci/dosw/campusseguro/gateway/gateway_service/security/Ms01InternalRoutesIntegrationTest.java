package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Rutas internas de MS01 (RF-MS01-09; EST §5: MS03 → MS01 y MS05 → MS01) con las políticas reales de
 * application.yaml: solo se llaman con clave de servicio y MS01 recibe el X-Service-Name derivado de ella.
 */
class Ms01InternalRoutesIntegrationTest extends GatewayIntegrationTest {

    private static final String MS03_KEY = "clave-de-servicio-ms03-de-al-menos-32-caracteres";
    private static final String MS05_KEY = "clave-de-servicio-ms05-de-al-menos-32-caracteres";
    private static final String BATCH_BODY = "{\"ids\":[\"3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42\"]}";

    private static final DownstreamStub MS01 = DownstreamStub.start();

    @DynamicPropertySource
    static void routesAndKeys(DynamicPropertyRegistry registry) {
        registry.add("MS01_URI", MS01::baseUrl);
        registry.add("GW_SERVICE_KEY_MS03", () -> MS03_KEY);
        registry.add("GW_SERVICE_KEY_MS05", () -> MS05_KEY);
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
    void shouldForwardTheBatchWithTheServiceNameAndWithoutTheKeyOrAUserIdentity() {
        anonymousClient().post().uri("/api/v1/users/batch")
                .header("X-Service-Key", MS03_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(BATCH_BODY)
                .exchange()
                .expectStatus().isOk();

        assertThat(MS01.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.body()).isEqualTo(BATCH_BODY);
            assertThat(request.header("X-Service-Name")).containsExactly("ms03");
            assertThat(request.header("X-Service-Key")).isEmpty();
            assertThat(request.header("X-User-Id")).isEmpty();
            assertThat(request.header("X-User-Role")).isEmpty();
        });
    }

    @Test
    void shouldForwardByRoleWithTheQueryParamsUnchanged() {
        anonymousClient().get()
                .uri("/api/v1/users/by-role?rol=BRIGADISTA&page=0&size=100&creado_antes=2026-11-07T15:04:05Z")
                .header("X-Service-Key", MS05_KEY)
                .exchange()
                .expectStatus().isOk();

        assertThat(MS01.requests()).singleElement().satisfies(request -> {
            assertThat(request.uri())
                    .isEqualTo("/api/v1/users/by-role?rol=BRIGADISTA&page=0&size=100&creado_antes=2026-11-07T15:04:05Z");
            assertThat(request.header("X-Service-Name")).containsExactly("ms05");
        });
    }

    @Test
    void shouldReplaceAServiceNameSentByTheCallerWithTheOneOfTheKey() {
        anonymousClient().get().uri("/api/v1/users/by-role?rol=ESTUDIANTE")
                .header("X-Service-Key", MS03_KEY)
                .header("X-Service-Name", "ms01")
                .exchange()
                .expectStatus().isOk();

        assertThat(MS01.requests()).singleElement()
                .satisfies(request -> assertThat(request.header("X-Service-Name")).containsExactly("ms03"));
    }

    @Test
    void shouldReturn401WithoutCallingMs01WhenTheInternalRoutesAreCalledWithoutAKey() {
        String body = anonymousClient().post().uri("/api/v1/users/batch")
                .header("X-Service-Name", "ms03")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(BATCH_BODY)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 401, "NO_AUTENTICADO", "Token ausente, inválido o expirado.");
        assertThat(MS01.requests()).isEmpty();
    }

    @Test
    void shouldReturn401ToAUserWithAValidJwtOnTheInternalRoutes() {
        client().get().uri("/api/v1/users/by-role?rol=BRIGADISTA").exchange().expectStatus().isUnauthorized();
        client().post().uri("/api/v1/users/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(BATCH_BODY)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(MS01.requests()).isEmpty();
    }

    @Test
    void shouldReturn401ForAnInvalidKey() {
        anonymousClient().get().uri("/api/v1/users/by-role?rol=BRIGADISTA")
                .header("X-Service-Key", "clave-robada-de-mas-de-32-caracteres-xxxx")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(MS01.requests()).isEmpty();
    }
}
