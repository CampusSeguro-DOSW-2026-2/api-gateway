package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** RNF-GW-06: una configuración JWT incompleta o inválida impide el arranque. */
class JwtPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "gateway.jwt.issuer=ms01-identity-and-access",
                    "gateway.jwt.audience=campusseguro",
                    "gateway.jwt.clock-skew=30s",
                    "gateway.jwt.max-lifetime=3600s");

    private void assertStartupFailsWith(String expectedCause, String... properties) {
        runner.withPropertyValues(properties).run(context ->
                assertThat(context).getFailure().hasStackTraceContaining(expectedCause));
    }

    @Test
    void shouldAcceptPemWithRealOrEscapedNewlinesOrOnlyTheBase64Body() {
        String pem = TestJwt.publicKeyPem();
        String base64Only = Base64.getEncoder().encodeToString(RsaPublicKeys.parse(pem).getEncoded());

        for (String key : new String[] {pem, pem.replace("\n", "\\n"), base64Only}) {
            assertThat(RsaPublicKeys.parse(key).getModulus().bitLength()).isEqualTo(2048);
        }
        runner.withPropertyValues("gateway.jwt.public-key=" + base64Only).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldFailToStartWithoutPublicKey() {
        assertStartupFailsWith("JWT_PUBLIC_KEY debe ser una llave pública RSA");
    }

    @Test
    void shouldFailToStartWithInvalidPublicKey() {
        assertStartupFailsWith("JWT_PUBLIC_KEY debe ser una llave pública RSA", "gateway.jwt.public-key=no-es-una-llave");
    }

    @Test
    void shouldFailToStartWithRsaKeyShorterThan2048Bits() {
        String weakKey = TestJwt.pem(TestJwt.newKeyPair(1024)).replace("\n", "\\n");

        assertStartupFailsWith("JWT_PUBLIC_KEY debe ser una llave pública RSA", "gateway.jwt.public-key=" + weakKey);
    }

    @Test
    void shouldFailToStartWithBlankIssuerOrNonPositiveLifetime() {
        String key = "gateway.jwt.public-key=" + TestJwt.publicKeyPem().replace("\n", "\\n");
        assertStartupFailsWith("JWT_ISSUER es obligatorio", key, "gateway.jwt.issuer=");
        assertStartupFailsWith("JWT_MAX_LIFETIME_SECONDS debe ser mayor que 0", key, "gateway.jwt.max-lifetime=0s");
    }
}
