package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** RNF-GW-06 y RN-GW-20: claves inválidas impiden el arranque sin mostrar su valor. */
class ServiceKeyPropertiesTest {

    private static final String KEY_32 = "k".repeat(32);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ServiceKeyProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void shouldIgnoreEmptyKeysOfServicesThatDoNotCall() {
        runner.withPropertyValues("gateway.service-keys.ms04=" + KEY_32, "gateway.service-keys.ms05=")
                .run(context -> assertThat(context.getBean(ServiceKeyProperties.class).configuredKeys())
                        .containsOnlyKeys("ms04"));
    }

    @Test
    void shouldStartWithoutAnyServiceKey() {
        runner.run(context -> assertThat(context.getBean(ServiceKeyProperties.class).configuredKeys()).isEmpty());
    }

    @Test
    void shouldFailToStartWithAKeyShorterThan32CharactersWithoutShowingIt() {
        String shortKey = "clave-corta-de-31-caracteres-xx";
        assertThat(shortKey).hasSize(31);

        runner.withPropertyValues("gateway.service-keys.ms04=" + shortKey).run(context -> assertThat(context)
                .getFailure()
                .hasStackTraceContaining("al menos 32 caracteres")
                .satisfies(failure -> assertThat(stackTraceOf(failure)).doesNotContain(shortKey)));
    }

    @Test
    void shouldFailToStartWithRepeatedKeysWithoutShowingThem() {
        runner.withPropertyValues("gateway.service-keys.ms04=" + KEY_32, "gateway.service-keys.ms05=" + KEY_32)
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("hay claves repetidas")
                        .satisfies(failure -> assertThat(stackTraceOf(failure)).doesNotContain(KEY_32)));
    }

    @Test
    void shouldFailToStartWithAnUnknownServiceName() {
        runner.withPropertyValues("gateway.service-keys.ms09=" + KEY_32).run(context ->
                assertThat(context).getFailure().hasStackTraceContaining("deben ser ms01…ms08"));
    }

    private static String stackTraceOf(Throwable failure) {
        java.io.StringWriter writer = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }
}
