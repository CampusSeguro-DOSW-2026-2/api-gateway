package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.eci.dosw.campusseguro.gateway.gateway_service.GatewayServiceApplication;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** RNF-GW-06 en el perfil cloud (ADR-006): un certificado o una CA que no cargan impiden el arranque. */
@Tag("integracion")
class CloudProfileStartupTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "contraseña incorrecta | classpath:mtls/gw-client.p12 | false | classpath:mtls/ca.pem        | password was incorrect",
        "keystore inexistente  | classpath:mtls/no-existe.p12 | true  | classpath:mtls/ca.pem        | Could not load store",
        "CA inexistente        | classpath:mtls/gw-client.p12 | true  | classpath:mtls/no-existe.pem | Could not load certificate"
    })
    void shouldFailToStartWithAnInvalidClientCertificateOrCa(String caso, String keystore, boolean realPassword,
            String ca, String expectedCause) {
        String keystorePassword = realPassword ? CloudMtlsIntegrationTest.clientStorePassword() : "otra-contrasena";

        assertThatThrownBy(() -> new SpringApplicationBuilder(GatewayServiceApplication.class)
                .profiles("test", "cloud")
                .properties(
                        "server.port=0",
                        "management.server.port=0",
                        "JWT_PUBLIC_KEY=" + TestJwt.publicKeyPem(),
                        "GW_CLIENT_KEYSTORE=" + keystore,
                        "GW_CLIENT_KEYSTORE_PASSWORD=" + keystorePassword,
                        "GW_TRUSTED_CA=" + ca)
                .run().close())
                .hasStackTraceContaining(expectedCause);
    }
}
