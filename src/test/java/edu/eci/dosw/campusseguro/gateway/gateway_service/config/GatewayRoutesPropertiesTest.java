package edu.eci.dosw.campusseguro.gateway.gateway_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** CA-GW-01-05: una tabla de rutas inválida impide el arranque y el mensaje indica la causa. */
class GatewayRoutesPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GatewayRoutesProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "gateway.downstream-timeout=3000ms",
                    "gateway.routes[0].id=ms01",
                    "gateway.routes[0].uri=http://ms01-identity-and-access:8081",
                    "gateway.routes[0].paths[0]=/api/v1/auth/**",
                    "gateway.routes[0].paths[1]=/api/v1/users");

    @Test
    void validTableIsBound() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            GatewayRoutesProperties properties = context.getBean(GatewayRoutesProperties.class);
            assertThat(properties.downstreamTimeout().toMillis()).isEqualTo(3000);
            assertThat(properties.routes()).singleElement()
                    .satisfies(route -> assertThat(route.paths()).hasSize(2));
        });
    }

    @Test
    void duplicatedPrefixFails() {
        assertStartupFailsWith("hay prefijos duplicados",
                "gateway.routes[1].id=ms08",
                "gateway.routes[1].uri=http://ms08-analytics-and-audit:8088",
                "gateway.routes[1].paths[0]=/api/v1/users");
    }

    @Test
    void duplicatedRouteIdFails() {
        assertStartupFailsWith("ids de ruta duplicados",
                "gateway.routes[1].id=ms01",
                "gateway.routes[1].uri=http://ms01-identity-and-access:8081",
                "gateway.routes[1].paths[0]=/api/v1/roles");
    }

    @Test
    void missingDestinationVariableFails() {
        assertStartupFailsWith("MS01_URI", "gateway.routes[0].uri=${MS01_URI}");
    }

    @Test
    void emptyDestinationFails() {
        assertStartupFailsWith("el destino (MSnn_URI) es obligatorio", "gateway.routes[0].uri=");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ms01-identity-and-access:8081", "ftp://ms01:21", "http://ms01:8081/api", "http://ms01:8081?x=1"})
    void invalidDestinationFails(String uri) {
        assertStartupFailsWith("el destino debe ser una URI http(s) absoluta", "gateway.routes[0].uri=" + uri);
    }

    @Test
    void malformedDestinationFails() {
        assertStartupFailsWith("gateway.routes[0].uri", "gateway.routes[0].uri=http://ms 01:8081");
    }

    @Test
    void prefixOutsideApiV1Fails() {
        assertStartupFailsWith("todo prefijo debe estar bajo /api/v1/", "gateway.routes[0].paths[0]=/auth/**");
    }

    @Test
    void nonPositiveTimeoutFails() {
        assertStartupFailsWith("GATEWAY_DOWNSTREAM_TIMEOUT_MS debe ser mayor que 0", "gateway.downstream-timeout=0ms");
    }

    @Test
    void emptyTableFails() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("gateway.downstream-timeout=3000ms")
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("la tabla de rutas no puede estar vacía"));
    }

    private void assertStartupFailsWith(String expectedCause, String... properties) {
        runner.withPropertyValues(properties).run(context ->
                assertThat(context).getFailure().hasStackTraceContaining(expectedCause));
    }
}
