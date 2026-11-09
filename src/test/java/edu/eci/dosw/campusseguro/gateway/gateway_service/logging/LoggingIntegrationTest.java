package edu.eci.dosw.campusseguro.gateway.gateway_service.logging;

import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.CapturedLogs;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** CA-GW-07-01, CA-GW-07-02 e INT-GW-01 (logs) a través del GW real. */
class LoggingIntegrationTest extends GatewayIntegrationTest {

    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final List<String> MINIMUM_FIELDS = List.of("timestamp", "level", "correlation_id", "method", "path",
            "status", "duration_ms", "upstream", "origen");
    private static final DownstreamStub MS01 = DownstreamStub.start();

    private CapturedLogs logs;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("MS01_URI", MS01::baseUrl);
    }

    @AfterAll
    static void stop() {
        MS01.close();
    }

    @BeforeEach
    void capture() {
        MS01.reset();
        logs = CapturedLogs.all();
    }

    @AfterEach
    void detach() {
        logs.close();
    }

    /** El servidor registra al terminar de escribir; el cliente puede leer la respuesta unos ms antes. */
    private Map<String, Object> accessLog(String path) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            var log = logs.asJson().stream()
                    .filter(json -> "Solicitud procesada".equals(json.get("mensaje")) && path.equals(json.get("path")))
                    .reduce((first, last) -> last);
            if (log.isPresent()) {
                return log.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Sin log de acceso para " + path);
    }

    @Test
    void shouldNotLogPasswordsTokensNorCredentialsOfLoginAndRegister() {
        String token = TestJwt.validToken();
        // Authorization y X-Service-Key juntas son 400 (RF-GW-08, A2): cada credencial va en su propia solicitud.
        for (String path : List.of("/api/v1/auth/login", "/api/v1/auth/register")) {
            for (String[] credential : List.of(new String[] {HttpHeaders.AUTHORIZATION, "Bearer " + token},
                    new String[] {"X-Service-Key", "clave-de-servicio-secreta-ms01"})) {
                anonymousClient().post().uri(path)
                        .header(credential[0], credential[1])
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"correo\":\"maria.perez@escuelaing.edu.co\",\"contrasena\":\"Campus#2026Secreta\"}")
                        .exchange()
                        .expectStatus().isOk();
            }
        }

        assertThat(MS01.requests()).hasSize(4);
        assertThat(logs.asJsonLines()).contains("Solicitud procesada")
                .doesNotContain("Campus#2026Secreta", token, "clave-de-servicio-secreta-ms01", "Bearer",
                        "Authorization", "maria.perez");
    }

    @Test
    void shouldLogEveryRequestWithTheMinimumFieldsOfTheStandards() throws InterruptedException {
        client().get().uri("/api/v1/users/me").header("X-Correlation-ID", CORRELATION_ID).exchange().expectStatus().isOk();

        assertThat(accessLog("/api/v1/users/me")).containsKeys(MINIMUM_FIELDS.toArray(String[]::new))
                .containsEntry("level", "INFO")
                .containsEntry("correlation_id", CORRELATION_ID)
                .containsEntry("status", 200)
                .containsEntry("upstream", "ms01")
                .containsEntry("origen", "externo");
    }

    @Test
    void shouldLogRequestsAnsweredByTheGatewayWithUpstreamGw() throws InterruptedException {
        anonymousClient().get().uri("/api/v1/inexistente").exchange().expectStatus().isNotFound();
        anonymousClient().get().uri("/api/v1/users/me").exchange().expectStatus().isUnauthorized();

        assertThat(accessLog("/api/v1/inexistente")).containsEntry("level", "WARN").containsEntry("upstream", "gw");
        assertThat(accessLog("/api/v1/users/me")).containsEntry("status", 401).containsEntry("upstream", "gw");
    }
}
