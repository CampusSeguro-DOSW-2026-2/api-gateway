package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/**
 * Una configuración de la consulta de cortes de sesión incompleta o inválida impide el arranque
 */
class SessionCutsPropertiesTest {

    private static final String ROUTE = "http://ms01-identity-and-access:8081/api/v1/internal/session-cuts";
    private static final String URL = "gateway.session-cuts.url=" + ROUTE;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(SessionCutsConfig.class);

    /**
     * Comprueba que el arranque falla con el motivo indicado
     *
     * @param expectedCause texto que debe aparecer en la causa del fallo
     * @param properties propiedades de la configuración
     */
    private void assertStartupFailsWith(String expectedCause, String... properties) {
        runner.withPropertyValues(properties).run(context ->
                assertThat(context).getFailure().hasStackTraceContaining(expectedCause));
    }

    /**
     * Con los tres valores válidos el arranque tiene éxito y los expone con sus tipos
     */
    @Test
    void shouldBindTheUrlTheIntervalAndTheTimeout() {
        runner.withPropertyValues(URL, "gateway.session-cuts.interval=4s", "gateway.session-cuts.timeout=3s")
                .run(context -> {
                    SessionCutsProperties properties = context.getBean(SessionCutsProperties.class);
                    assertThat(properties.url()).isEqualTo(URI.create(ROUTE));
                    assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(4));
                    assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(3));
                });
    }

    /**
     * Sin la dirección el gateway no arranca
     */
    @Test
    void shouldFailToStartWithoutTheUrl() {
        assertStartupFailsWith("SESSION_CUTS_URL es obligatorio",
                "gateway.session-cuts.interval=4s", "gateway.session-cuts.timeout=3s");
    }

    /**
     * Sin el intervalo el gateway no arranca
     */
    @Test
    void shouldFailToStartWithoutTheInterval() {
        assertStartupFailsWith("SESSION_CUTS_INTERVAL_SECONDS es obligatorio", URL, "gateway.session-cuts.timeout=3s");
    }

    /**
     * Sin el tiempo de espera el gateway no arranca
     */
    @Test
    void shouldFailToStartWithoutTheTimeout() {
        assertStartupFailsWith("SESSION_CUTS_TIMEOUT_SECONDS es obligatorio", URL, "gateway.session-cuts.interval=4s");
    }

    /**
     * Una dirección que no es http(s) absoluta impide el arranque
     *
     * @param url dirección inválida
     */
    @ParameterizedTest
    @ValueSource(strings = {"ftp://ms01:8081/cortes", "/api/v1/internal/session-cuts", "ms01:8081"})
    void shouldFailToStartWithAnUrlThatIsNotAbsoluteHttp(String url) {
        assertStartupFailsWith("SESSION_CUTS_URL debe ser una URI http(s) absoluta",
                "gateway.session-cuts.url=" + url, "gateway.session-cuts.interval=4s",
                "gateway.session-cuts.timeout=3s");
    }

    /**
     * Un intervalo o un tiempo de espera en cero o negativo impide el arranque
     *
     * @param interval intervalo configurado
     * @param timeout tiempo de espera configurado
     */
    @ParameterizedTest
    @CsvSource({"0s,3s", "4s,0s", "-1s,3s", "4s,-2s"})
    void shouldFailToStartWithADurationThatIsNotPositive(String interval, String timeout) {
        assertStartupFailsWith("deben ser mayores que 0", URL, "gateway.session-cuts.interval=" + interval,
                "gateway.session-cuts.timeout=" + timeout);
    }
}
