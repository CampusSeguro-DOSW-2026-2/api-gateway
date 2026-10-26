package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;

/** Contrato de las políticas de acceso de application.yaml contra GW-C §3 y los Estándares §2. */
class AccessPolicyResolverTest {

    private final AccessPolicyResolver resolver = new AccessPolicyResolver(loadApplicationYaml());

    private static AccessPolicyProperties loadApplicationYaml() {
        try {
            var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
            return new Binder(ConfigurationPropertySources.from(sources))
                    .bind("gateway.access", AccessPolicyProperties.class).get();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
        "GET,    /actuator/health,             PUBLICA",
        "POST,   /api/v1/auth/register,        PUBLICA",
        "POST,   /api/v1/auth/login,           PUBLICA",
        "POST,   /api/v1/auth/password/forgot, PUBLICA",
        "POST,   /api/v1/auth/password/reset,  PUBLICA",
        "POST,   /api/v1/audit/events,         INTERNA",
        "GET,    /api/v1/auth/register,        PROTEGIDA",
        "GET,    /api/v1/auth/login,           PROTEGIDA",
        "POST,   /api/v1/auth/logout,          PROTEGIDA",
        "GET,    /api/v1/users/me,             PROTEGIDA",
        "POST,   /api/v1/auth/login/,          PROTEGIDA",
        "GET,    /api/v1/audit/events,         PROTEGIDA",
        "DELETE, /api/v1/nueva-ruta,           PROTEGIDA"
    })
    void shouldClassifyByExactMethodAndPath(String method, String path, AccessPolicy expected) {
        assertThat(resolver.resolve(HttpMethod.valueOf(method), path)).isEqualTo(expected);
    }

    @Test
    void shouldDeclareOnlyTheFivePublicRoutesOfTheStandards() {
        assertThat(loadApplicationYaml().publicRoutes()).hasSize(5);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AccessPolicyProperties.class)
    static class PropertiesConfig {
    }

    @ParameterizedTest
    @CsvSource({"/api/v1/auth/**", "/api/v1/users/{id}", "/api/v1/auth/log?n"})
    void shouldFailToStartWhenAPublicRouteHasWildcards(String path) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(
                        "gateway.access.public-routes[0].method=POST",
                        "gateway.access.public-routes[0].path=" + path,
                        "gateway.access.internal-routes[0].method=POST",
                        "gateway.access.internal-routes[0].path=/api/v1/audit/events")
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("la ruta debe ser exacta, sin comodines"));
    }
}
