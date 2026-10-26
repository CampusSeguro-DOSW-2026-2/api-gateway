package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.CapturedLogs;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub.RecordedRequest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * INT-GW-11, INT-GW-12 y CA-GW-08-01 a CA-GW-08-04 con dobles de MS01, MS05 y MS08. Las rutas de MS05
 * y MS08 solo existen aquí: en application.yaml se agregan cuando sus OpenAPI se publiquen (RN-GW-03).
 */
class InternalCallsIntegrationTest extends GatewayIntegrationTest {

    private static final String MS04_KEY = "clave-de-servicio-ms04-de-al-menos-32-caracteres";
    private static final String MS01_KEY = "clave-de-servicio-ms01-de-al-menos-32-caracteres";
    private static final String STUDENT_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private static final DownstreamStub MS01 = DownstreamStub.start();
    private static final DownstreamStub MS05 = DownstreamStub.start();
    private static final DownstreamStub MS08 = DownstreamStub.start();

    private CapturedLogs logs;

    @DynamicPropertySource
    static void routesAndKeys(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes[0].id", () -> "ms01");
        registry.add("gateway.routes[0].uri", MS01::baseUrl);
        registry.add("gateway.routes[0].paths[0]", () -> "/api/v1/users/**");
        registry.add("gateway.routes[1].id", () -> "ms05");
        registry.add("gateway.routes[1].uri", MS05::baseUrl);
        registry.add("gateway.routes[1].paths[0]", () -> "/api/v1/assistance/**");
        registry.add("gateway.routes[2].id", () -> "ms08");
        registry.add("gateway.routes[2].uri", MS08::baseUrl);
        registry.add("gateway.routes[2].paths[0]", () -> "/api/v1/audit/events");
        registry.add("gateway.access.internal-routes[0].method", () -> "POST");
        registry.add("gateway.access.internal-routes[0].path", () -> "/api/v1/audit/events");
        registry.add("gateway.access.internal-routes[1].method", () -> "POST");
        registry.add("gateway.access.internal-routes[1].path", () -> "/api/v1/assistance/requests");
        registry.add("GW_SERVICE_KEY_MS04", () -> MS04_KEY);
        registry.add("GW_SERVICE_KEY_MS01", () -> MS01_KEY);
    }

    @AfterAll
    static void stop() {
        MS01.close();
        MS05.close();
        MS08.close();
    }

    @BeforeEach
    void reset() {
        MS01.reset();
        MS05.reset();
        MS08.reset();
        logs = CapturedLogs.all();
    }

    @AfterEach
    void detach() {
        logs.close();
    }

    @Test
    void shouldForwardAnInternalCallWithTheServiceNameAndTheUserIdentityButWithoutTheKey() {
        anonymousClient().post().uri("/api/v1/assistance/requests")
                .header("X-Service-Key", MS04_KEY)
                .header("X-User-Id", STUDENT_ID)
                .header("X-User-Role", "ESTUDIANTE")
                .header("X-Correlation-ID", CORRELATION_ID)
                .exchange()
                .expectStatus().isOk();

        assertThat(MS05.requests()).singleElement().satisfies(request -> {
            assertThat(request.header("X-Service-Name")).containsExactly("ms04");
            assertThat(request.header("X-User-Id")).containsExactly(STUDENT_ID);
            assertThat(request.header("X-User-Role")).containsExactly("ESTUDIANTE");
            assertThat(request.header("X-Correlation-ID")).containsExactly(CORRELATION_ID);
            assertThat(request.header("X-Service-Key")).isEmpty();
        });
    }

    @Test
    void shouldLogTheInternalCallWithTheCallerAsOriginAndNeverTheKey() throws InterruptedException {
        anonymousClient().post().uri("/api/v1/audit/events")
                .header("X-Service-Key", MS01_KEY)
                .header("X-Correlation-ID", CORRELATION_ID)
                .exchange()
                .expectStatus().isOk();

        Map<String, Object> accessLog = awaitAccessLog("/api/v1/audit/events");
        assertThat(accessLog).containsEntry("origen", "ms01")
                .containsEntry("upstream", "ms08")
                .containsEntry("correlation_id", CORRELATION_ID);
        assertThat(logs.asJsonLines()).doesNotContain(MS01_KEY);
        assertThat(MS08.requests()).singleElement()
                .satisfies(request -> assertThat(request.header("X-Service-Name")).containsExactly("ms01"));
    }

    @Test
    void shouldReturn401WithoutCallingTheDestinationForAnInvalidKey() {
        String body = anonymousClient().post().uri("/api/v1/assistance/requests")
                .header("X-Service-Key", "clave-robada-de-mas-de-32-caracteres-xxxx")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 401, "NO_AUTENTICADO", "Token ausente, inválido o expirado.");
        assertThat(MS05.requests()).isEmpty();
    }

    @Test
    void shouldReturn401ToAnExternalClientOnTheAuditRouteEvenWithAValidJwt() {
        anonymousClient().post().uri("/api/v1/audit/events").exchange().expectStatus().isUnauthorized();
        client().post().uri("/api/v1/audit/events").exchange().expectStatus().isUnauthorized();

        assertThat(MS08.requests()).isEmpty();
    }

    @Test
    void shouldReturn400WhenAuthorizationAndServiceKeyComeTogether() {
        anonymousClient().get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validToken())
                .header("X-Service-Key", MS04_KEY)
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(MS01.requests()).isEmpty();
    }

    @Test
    void shouldAcceptAServiceKeyOnAProtectedRouteAndForwardTheUserItActsFor() {
        anonymousClient().get().uri("/api/v1/users/me")
                .header("X-Service-Key", MS04_KEY)
                .header("X-User-Id", STUDENT_ID)
                .header("X-User-Role", "ESTUDIANTE")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = MS01.requests().get(0);
        assertThat(request.header("X-Service-Name")).containsExactly("ms04");
        assertThat(request.header("X-User-Role")).containsExactly("ESTUDIANTE");
    }

    @Test
    void shouldReturn400ForAnIncompletePropagatedIdentity() {
        anonymousClient().post().uri("/api/v1/assistance/requests")
                .header("X-Service-Key", MS04_KEY)
                .header("X-User-Id", STUDENT_ID)
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(MS05.requests()).isEmpty();
    }

    private Map<String, Object> awaitAccessLog(String path) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            var log = logs.asJson().stream()
                    .filter(json -> "Solicitud procesada".equals(json.get("mensaje")) && path.equals(json.get("path")))
                    .findFirst();
            if (log.isPresent()) {
                return log.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Sin log de acceso para " + path);
    }
}
