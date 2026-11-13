package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.CapturedLogs;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cortes de sesión de punta a punta (RF-MS01-11): el gateway consulta un doble de MS01, aplica los cortes a los
 * tokens y los levanta cuando MS01 deja de publicarlos. Usa su propio doble de cortes para no afectar a las demás
 * pruebas de integración.
 */
class SessionCutsIntegrationTest extends GatewayIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String ME = "/api/v1/users/me";
    private static final DownstreamStub MS01 = DownstreamStub.start();
    private static final DownstreamStub CUTS = DownstreamStub.start().respond(200, NO_CUTS);

    private CapturedLogs logs;

    /**
     * Apunta el gateway a los dobles de esta prueba
     *
     * @param registry registro de propiedades de la prueba
     */
    @DynamicPropertySource
    static void routesAndCuts(DynamicPropertyRegistry registry) {
        registry.add("MS01_URI", MS01::baseUrl);
        registry.add("gateway.session-cuts.url", () -> CUTS.baseUrl() + "/api/v1/internal/session-cuts");
    }

    /**
     * Cierra los dobles al terminar la clase
     */
    @AfterAll
    static void stop() {
        MS01.close();
        CUTS.close();
    }

    /**
     * Deja los dobles sin cortes ni solicitudes y empieza a capturar los logs
     */
    @BeforeEach
    void reset() {
        MS01.reset();
        CUTS.reset();
        CUTS.respond(200, NO_CUTS);
        logs = CapturedLogs.all();
    }

    /**
     * Quita los cortes y espera a que el gateway los vea, para no afectar a la prueba siguiente
     */
    @AfterEach
    void clearCuts() {
        CUTS.respond(200, NO_CUTS);
        logs.close();
        await().atMost(WAIT).untilAsserted(() -> client().get().uri(ME).exchange().expectStatus().isOk());
    }

    /**
     * Arma una instantánea con un corte de usuario
     *
     * @param userId id del usuario cortado
     * @param validFrom instante del corte
     * @return el cuerpo JSON de la instantánea
     */
    private static String userCut(String userId, Instant validFrom) {
        return "{\"generado_en\":\"" + Instant.now() + "\",\"ventana_segundos\":3700,\"usuarios\":[{\"id\":\""
                + userId + "\",\"valido_desde\":\"" + validFrom + "\"}],\"roles\":[]}";
    }

    /**
     * Arma una instantánea con un corte de rol
     *
     * @param role nombre del rol cortado
     * @param validFrom instante del corte
     * @return el cuerpo JSON de la instantánea
     */
    private static String roleCut(String role, Instant validFrom) {
        return "{\"generado_en\":\"" + Instant.now() + "\",\"ventana_segundos\":3700,\"usuarios\":[],"
                + "\"roles\":[{\"rol\":\"" + role + "\",\"valido_desde\":\"" + validFrom + "\"}]}";
    }

    /**
     * Sin cortes, un token válido pasa
     */
    @Test
    void shouldAcceptAValidTokenWhileThereAreNoCuts() {
        client().get().uri(ME).exchange().expectStatus().isOk();

        assertThat(MS01.requests()).hasSize(1);
    }

    /**
     * El corte de un usuario rechaza su token con el 401 genérico, sin llamar a MS01, y al dejar de publicarse el
     * token vuelve a aceptarse
     */
    @Test
    void shouldRejectATokenCutByTheUserAndAcceptItAgainWhenTheCutIsNoLongerPublished() {
        String token = TestJwt.validToken();
        CUTS.respond(200, userCut(TestJwt.USER_ID, Instant.now().plusSeconds(60)));

        await().atMost(WAIT).untilAsserted(() -> anonymousClient().get().uri(ME)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange().expectStatus().isUnauthorized());
        MS01.reset();
        String body = anonymousClient().get().uri(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();
        assertGatewayError(body, 401, "NO_AUTENTICADO", "Token ausente, inválido o expirado.");
        assertThat(MS01.requests()).isEmpty();

        CUTS.respond(200, NO_CUTS);

        await().atMost(WAIT).untilAsserted(() -> anonymousClient().get().uri(ME)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange().expectStatus().isOk());
    }

    /**
     * Un token emitido después del corte no se ve afectado
     */
    @Test
    void shouldAcceptATokenIssuedAfterTheCut() {
        CUTS.respond(200, userCut(TestJwt.USER_ID, Instant.now().minusSeconds(120)));
        int queriesBefore = CUTS.requests().size();
        await().atMost(WAIT).until(() -> CUTS.requests().size() > queriesBefore + 1);

        client().get().uri(ME).exchange().expectStatus().isOk();
    }

    /**
     * El corte de un rol rechaza a los usuarios que lo tienen y no a los demás
     */
    @Test
    void shouldRejectOnlyTheTokensThatHaveTheCutRole() {
        String student = TestJwt.sign(TestJwt.validClaims(Instant.now())
                .claim("roles", List.of("ESTUDIANTE")).build());
        CUTS.respond(200, roleCut("BRIGADISTA", Instant.now().plusSeconds(60)));

        await().atMost(WAIT).untilAsserted(() -> client().get().uri(ME).exchange().expectStatus().isUnauthorized());
        anonymousClient().get().uri(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                .exchange().expectStatus().isOk();
    }

    /**
     * El corte de un usuario no afecta a otro usuario
     */
    @Test
    void shouldNotAffectOtherUsers() {
        CUTS.respond(200, userCut("11111111-2222-4333-8444-555555555555", Instant.now().plusSeconds(60)));
        int queriesBefore = CUTS.requests().size();
        await().atMost(WAIT).until(() -> CUTS.requests().size() > queriesBefore + 1);

        client().get().uri(ME).exchange().expectStatus().isOk();
    }

    /**
     * Si MS01 deja de responder, el gateway conserva el último corte, deja un aviso en el log y no bloquea el
     * tráfico de quienes no están cortados
     */
    @Test
    void shouldKeepTheLastCutsAndWarnWhenMs01StopsAnswering() {
        String student = TestJwt.sign(TestJwt.validClaims(Instant.now())
                .claim("roles", List.of("ESTUDIANTE")).build());
        CUTS.respond(200, roleCut("BRIGADISTA", Instant.now().plusSeconds(60)));
        await().atMost(WAIT).untilAsserted(() -> client().get().uri(ME).exchange().expectStatus().isUnauthorized());

        CUTS.respond(500, "{\"status\":500,\"codigo\":\"ERROR_INTERNO\",\"mensaje\":\"x\",\"errores\":[]}");
        int queriesBefore = CUTS.requests().size();
        await().atMost(WAIT).until(() -> CUTS.requests().size() > queriesBefore + 1);

        client().get().uri(ME).exchange().expectStatus().isUnauthorized();
        anonymousClient().get().uri(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + student)
                .exchange().expectStatus().isOk();
        assertThat(logs.events()).anySatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("WARN");
            assertThat(event.getFormattedMessage()).contains("No se pudieron consultar los cortes de sesión");
        });
    }

    /**
     * El gateway se identifica ante la ruta interna de MS01 como `gateway`, sin credenciales de usuario
     */
    @Test
    void shouldAskMs01ForTheCutsAsTheGateway() {
        await().atMost(WAIT).until(() -> !CUTS.requests().isEmpty());

        DownstreamStub.RecordedRequest request = CUTS.requests().get(0);
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri()).isEqualTo("/api/v1/internal/session-cuts");
        assertThat(request.header("X-Service-Name")).containsExactly("gateway");
        assertThat(request.header("Authorization")).isEmpty();
        assertThat(request.header("X-Service-Key")).isEmpty();
    }

    /**
     * La consulta de cortes no pasa por MS01 como una petición de usuario ni se mezcla con su tráfico
     */
    @Test
    void shouldNotSendTheCutsQueryToTheRoutedMs01() {
        await().atMost(WAIT).until(() -> CUTS.requests().size() > 1);

        assertThat(MS01.requests()).isEmpty();
    }
}
