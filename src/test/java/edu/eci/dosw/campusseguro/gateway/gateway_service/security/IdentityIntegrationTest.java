package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub.RecordedRequest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** INT-GW-02, INT-GW-04, INT-GW-05 y CA-GW-03-03 a CA-GW-03-05 con el doble de MS01. */
class IdentityIntegrationTest extends GatewayIntegrationTest {

    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
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

    private static RecordedRequest receivedByMs01() {
        assertThat(MS01.requests()).hasSize(1);
        return MS01.requests().get(0);
    }

    private static String studentToken() {
        return TestJwt.sign(TestJwt.validClaims(Instant.now()).claim("roles", List.of("ESTUDIANTE")).build());
    }

    @Test
    void shouldForwardIdentityAndCorrelationWithoutTheToken() {
        client().get().uri("/api/v1/users/me").header("X-Correlation-ID", CORRELATION_ID)
                .exchange().expectStatus().isOk();

        RecordedRequest request = receivedByMs01();
        assertThat(request.header("X-User-Id")).containsExactly(TestJwt.USER_ID);
        assertThat(request.header("X-User-Role")).containsExactly("BRIGADISTA,ESTUDIANTE");
        assertThat(request.header("X-Correlation-ID")).containsExactly(CORRELATION_ID);
        assertThat(request.header(HttpHeaders.AUTHORIZATION)).isEmpty();
    }

    @Test
    void shouldForwardOnlyTheTokenIdentityWhenTheClientSpoofsHeaders() {
        anonymousClient().get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken())
                .header("X-User-Id", "11111111-1111-4111-8111-111111111111")
                .header("x-user-role", "ADMIN")
                .header("X-Service-Name", "ms08")
                .header("X-Service-Key", "clave-robada")
                .exchange().expectStatus().isOk();

        RecordedRequest request = receivedByMs01();
        assertThat(request.header("X-User-Id")).containsExactly(TestJwt.USER_ID);
        assertThat(request.header("X-User-Role")).containsExactly("ESTUDIANTE");
        assertThat(request.header("X-Service-Name")).isEmpty();
        assertThat(request.header("X-Service-Key")).isEmpty();
    }

    @Test
    void shouldNotForwardIdentityNorCredentialsOnPublicRoutes() {
        anonymousClient().post().uri("/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validToken())
                .header("X-User-Id", TestJwt.USER_ID)
                .header("X-User-Role", "ADMIN")
                .header("X-Service-Name", "ms01")
                .header("X-Service-Key", "clave-robada")
                .exchange().expectStatus().isOk();

        RecordedRequest request = receivedByMs01();
        for (String header : List.of("X-User-Id", "X-User-Role", "X-Service-Name", "X-Service-Key",
                HttpHeaders.AUTHORIZATION)) {
            assertThat(request.header(header)).as(header).isEmpty();
        }
    }

    @Test
    void shouldPropagateTheMs01ForbiddenResponseUnchangedForAStudent() {
        String forbidden = "{\"status\":403,\"codigo\":\"PROHIBIDO\",\"mensaje\":\"No cuenta con los permisos "
                + "suficientes para realizar esta operación.\",\"errores\":[],\"correlation_id\":\"" + CORRELATION_ID + "\"}";
        MS01.respond(403, forbidden);

        anonymousClient().patch().uri("/api/v1/users/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken())
                .header("X-Correlation-ID", CORRELATION_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody(String.class).isEqualTo(forbidden);

        assertThat(receivedByMs01().header("X-User-Role")).containsExactly("ESTUDIANTE");
    }
}
