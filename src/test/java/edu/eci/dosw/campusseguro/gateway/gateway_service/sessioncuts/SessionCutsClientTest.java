package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub.RecordedRequest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

/**
 * Consulta de la instantánea de cortes de sesión a un doble de MS01
 */
class SessionCutsClientTest {

    private static final String PATH = "/api/v1/internal/session-cuts";
    private static final String USER_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final DownstreamStub MS01 = DownstreamStub.start();

    /**
     * Cierra el doble de MS01 al terminar la clase
     */
    @AfterAll
    static void stop() {
        MS01.close();
    }

    /**
     * Deja el doble de MS01 sin solicitudes registradas
     */
    @BeforeEach
    void reset() {
        MS01.reset();
    }

    /**
     * Crea el cliente apuntando al doble de MS01
     *
     * @param timeout tiempo máximo de espera de la consulta
     * @return el cliente listo para consultar
     */
    private static SessionCutsClient clientWith(Duration timeout) {
        return new SessionCutsClient(HttpClient.create(),
                new SessionCutsProperties(URI.create(MS01.baseUrl() + PATH), Duration.ofSeconds(4), timeout));
    }

    /**
     * Arma el cuerpo de una instantánea
     *
     * @param users fragmento JSON con la lista de usuarios
     * @param roles fragmento JSON con la lista de roles
     * @return el cuerpo JSON de la respuesta
     */
    private static String snapshotJson(String users, String roles) {
        return "{\"generado_en\":\"2026-11-10T23:05:20Z\",\"ventana_segundos\":3700,\"usuarios\":" + users
                + ",\"roles\":" + roles + "}";
    }

    /**
     * La respuesta de MS01 se convierte en los dos mapas de cortes
     */
    @Test
    void shouldConvertTheResponseIntoTheCutsByUserAndByRole() {
        MS01.respond(200, snapshotJson(
                "[{\"id\":\"" + USER_ID.toUpperCase() + "\",\"valido_desde\":\"2026-11-10T23:04:57Z\"}]",
                "[{\"rol\":\"BRIGADISTA\",\"valido_desde\":\"2026-11-10T22:58:03Z\"}]"));

        StepVerifier.create(clientWith(Duration.ofSeconds(3)).fetch())
                .assertNext(snapshot -> {
                    assertThat(snapshot.users()).containsOnly(entry(USER_ID, Instant.parse("2026-11-10T23:04:57Z")));
                    assertThat(snapshot.roles())
                            .containsOnly(entry("BRIGADISTA", Instant.parse("2026-11-10T22:58:03Z")));
                })
                .verifyComplete();
    }

    /**
     * Una instantánea sin cortes es válida
     */
    @Test
    void shouldAcceptASnapshotWithoutCuts() {
        MS01.respond(200, snapshotJson("[]", "[]"));

        StepVerifier.create(clientWith(Duration.ofSeconds(3)).fetch())
                .assertNext(snapshot -> assertThat(snapshot).isEqualTo(SessionCutSnapshot.EMPTY))
                .verifyComplete();
    }

    /**
     * Los campos que el gateway no usa o que no conoce se ignoran
     */
    @Test
    void shouldIgnoreFieldsItDoesNotKnow() {
        MS01.respond(200, "{\"usuarios\":[{\"id\":\"" + USER_ID + "\",\"valido_desde\":\"2026-11-10T23:04:57Z\","
                + "\"motivo\":\"cambio de roles\"}],\"roles\":[],\"otro\":1}");

        StepVerifier.create(clientWith(Duration.ofSeconds(3)).fetch())
                .assertNext(snapshot -> assertThat(snapshot.users()).containsKey(USER_ID))
                .verifyComplete();
    }

    /**
     * La consulta es un GET a la ruta interna con la identidad del gateway y sin credenciales de usuario
     */
    @Test
    void shouldAskMs01AsTheGatewayWithoutAnyUserCredential() {
        MS01.respond(200, snapshotJson("[]", "[]"));

        StepVerifier.create(clientWith(Duration.ofSeconds(3)).fetch()).expectNextCount(1).verifyComplete();

        RecordedRequest request = MS01.requests().get(0);
        assertThat(MS01.requests()).hasSize(1);
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri()).isEqualTo(PATH);
        assertThat(request.header("X-Service-Name")).containsExactly("gateway");
        assertThat(request.header("Accept")).containsExactly("application/json");
        assertThat(request.header("Authorization")).isEmpty();
        assertThat(request.header("X-Service-Key")).isEmpty();
        assertThat(request.header("X-User-Id")).isEmpty();
        assertThat(request.header("X-User-Role")).isEmpty();
        assertThat(request.header("X-User-Permission")).isEmpty();
        assertThat(UUID.fromString(request.header("X-Correlation-ID").get(0)).version()).isEqualTo(4);
    }

    /**
     * Cada consulta lleva un identificador de correlación distinto
     */
    @Test
    void shouldUseANewCorrelationIdOnEveryQuery() {
        MS01.respond(200, snapshotJson("[]", "[]"));
        SessionCutsClient client = clientWith(Duration.ofSeconds(3));

        StepVerifier.create(client.fetch()).expectNextCount(1).verifyComplete();
        StepVerifier.create(client.fetch()).expectNextCount(1).verifyComplete();

        assertThat(MS01.requests().get(0).header("X-Correlation-ID"))
                .isNotEqualTo(MS01.requests().get(1).header("X-Correlation-ID"));
    }

    /**
     * Una respuesta de error de MS01 hace fallar la consulta
     *
     * @param status código de estado que responde MS01
     */
    @ParameterizedTest
    @CsvSource({"400", "401", "403", "404", "500", "502", "503"})
    void shouldFailWhenMs01AnswersAnError(int status) {
        MS01.respond(status, "{\"status\":" + status + ",\"codigo\":\"ERROR\",\"mensaje\":\"x\",\"errores\":[]}");

        StepVerifier.create(clientWith(Duration.ofSeconds(3)).fetch()).expectError().verify();
    }

    /**
     * Un cuerpo que no es una instantánea válida hace fallar la consulta
     *
     * @param body cuerpo inválido que responde MS01
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "no es json",
        "{}",
        "{\"usuarios\":[]}",
        "{\"roles\":[]}",
        "{\"usuarios\":null,\"roles\":[]}",
        "{\"usuarios\":[{\"valido_desde\":\"2026-11-10T23:04:57Z\"}],\"roles\":[]}",
        "{\"usuarios\":[{\"id\":\"3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42\"}],\"roles\":[]}",
        "{\"usuarios\":[{\"id\":\"3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42\",\"valido_desde\":\"ayer\"}],\"roles\":[]}",
        "{\"usuarios\":[null],\"roles\":[]}",
        "{\"usuarios\":[],\"roles\":[{\"valido_desde\":\"2026-11-10T22:58:03Z\"}]}",
        "{\"usuarios\":[],\"roles\":[{\"rol\":\"BRIGADISTA\"}]}",
        "{\"usuarios\":[],\"roles\":[{\"rol\":\"BRIGADISTA\",\"valido_desde\":\"2026-13-40\"}]}"
    })
    void shouldFailWhenTheBodyIsNotAValidSnapshot(String body) {
        MS01.respond(200, body);

        StepVerifier.create(clientWith(Duration.ofSeconds(3)).fetch()).expectError().verify();
    }

    /**
     * Si MS01 tarda más que el tiempo máximo de espera, la consulta falla por tiempo agotado
     */
    @Test
    void shouldFailWhenMs01DoesNotAnswerInTime() {
        MS01.respond(200, snapshotJson("[]", "[]")).delay(Duration.ofMillis(600));

        StepVerifier.create(clientWith(Duration.ofMillis(150)).fetch())
                .expectError(TimeoutException.class)
                .verify();
    }

    /**
     * Si no se puede abrir la conexión con MS01, la consulta falla
     */
    @Test
    void shouldFailWhenMs01IsNotReachable() {
        SessionCutsClient client = new SessionCutsClient(HttpClient.create(),
                new SessionCutsProperties(URI.create("http://localhost:1" + PATH), Duration.ofSeconds(4),
                        Duration.ofSeconds(3)));

        StepVerifier.create(client.fetch()).expectError().verify();
    }
}
