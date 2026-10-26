package edu.eci.dosw.campusseguro.gateway.gateway_service.cors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;

/** CA-GW-04-04 y RNF-GW-06: una política CORS inválida impide el arranque. */
class CorsPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues("gateway.cors.max-age=3600s");

    private void assertStartupFailsWith(String expectedCause, String... properties) {
        runner.withPropertyValues(properties).run(context ->
                assertThat(context).getFailure().hasStackTraceContaining(expectedCause));
    }

    @Test
    void shouldBuildThePolicyOfTheStandardsFromCommaSeparatedOrigins() {
        runner.withPropertyValues("gateway.cors.allowed-origins=http://localhost:5173, https://campusseguro.edu.co")
                .run(context -> {
                    CorsConfiguration configuration = context.getBean(CorsProperties.class).toCorsConfiguration();
                    assertThat(configuration.getAllowedOrigins())
                            .containsExactly("http://localhost:5173", "https://campusseguro.edu.co");
                    assertThat(configuration.getAllowedMethods())
                            .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
                    assertThat(configuration.getAllowedHeaders())
                            .containsExactly("Authorization", "Content-Type", "Accept", "X-Correlation-ID");
                    assertThat(configuration.getExposedHeaders()).containsExactly("X-Correlation-ID");
                    assertThat(configuration.getAllowCredentials()).isFalse();
                    assertThat(configuration.getMaxAge()).isEqualTo(3600);
                });
    }

    @Test
    void shouldFailToStartWithWildcardAndCredentials() {
        assertStartupFailsWith("no puede incluir * si CORS_ALLOW_CREDENTIALS=true",
                "gateway.cors.allowed-origins=*", "gateway.cors.allow-credentials=true");
    }

    @Test
    void shouldAllowWildcardWithoutCredentials() {
        runner.withPropertyValues("gateway.cors.allowed-origins=*").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldFailToStartWithoutAllowedOrigins() {
        assertStartupFailsWith("CORS_ALLOWED_ORIGINS es obligatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173/", "localhost:5173", "http://localhost:5173/app"})
    void shouldFailToStartWithMalformedOrigin(String origin) {
        assertStartupFailsWith("debe ser esquema://host[:puerto]", "gateway.cors.allowed-origins=" + origin);
    }

    @Test
    void shouldFailToStartWithNegativeMaxAge() {
        assertStartupFailsWith("CORS_MAX_AGE_SECONDS no puede ser negativo",
                "gateway.cors.allowed-origins=http://localhost:5173", "gateway.cors.max-age=-1s");
    }
}
