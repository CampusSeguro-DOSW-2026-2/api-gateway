package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.MtlsStub;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * INT-GW-16 (ADR-006): con el perfil cloud, el GW llega a los MS por mTLS y un TLS rechazado es 502.
 *
 * <p>Los certificados de src/test/resources/mtls salen de infra/mtls/generar-ca.sh y emitir-servidor.sh (CA de
 * prueba, cliente del GW y servidor para localhost); otra-ca.pem y servidor-otra-ca.p12 son de una segunda CA.
 * Solo sirven para estas pruebas.
 */
@ActiveProfiles("cloud")
class CloudMtlsIntegrationTest extends GatewayIntegrationTest {

    /** MS que confía en la CA del GW y presenta un certificado de esa CA. */
    private static final MtlsStub MS01 = MtlsStub.start("mtls/servidor.p12", "mtls/ca.pem");
    /** MS cuyo certificado de servidor es de otra CA: el GW no debe confiar en él. */
    private static final MtlsStub FOREIGN_SERVER = MtlsStub.start("mtls/servidor-otra-ca.p12", "mtls/ca.pem");
    /** MS que solo acepta clientes de otra CA (p. ej. el entorno equivocado): rechaza al GW. */
    private static final MtlsStub REJECTS_GATEWAY = MtlsStub.start("mtls/servidor.p12", "mtls/otra-ca.pem");

    @DynamicPropertySource
    static void cloudRoutes(DynamicPropertyRegistry registry) {
        registry.add("GW_CLIENT_KEYSTORE", () -> "classpath:mtls/gw-client.p12");
        registry.add("GW_CLIENT_KEYSTORE_PASSWORD", CloudMtlsIntegrationTest::clientStorePassword);
        registry.add("GW_TRUSTED_CA", () -> "classpath:mtls/ca.pem");
        registry.add("gateway.routes[0].id", () -> "ms01");
        registry.add("gateway.routes[0].uri", MS01::baseUrl);
        registry.add("gateway.routes[0].paths[0]", () -> "/api/v1/auth/**");
        registry.add("gateway.routes[1].id", () -> "ms02");
        registry.add("gateway.routes[1].uri", FOREIGN_SERVER::baseUrl);
        registry.add("gateway.routes[1].paths[0]", () -> "/api/v1/campus/**");
        registry.add("gateway.routes[2].id", () -> "ms03");
        registry.add("gateway.routes[2].uri", REJECTS_GATEWAY::baseUrl);
        registry.add("gateway.routes[2].paths[0]", () -> "/api/v1/alerts/**");
    }

    static String clientStorePassword() {
        try {
            return new ClassPathResource("mtls/gw-client.pass").getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void stop() {
        MS01.close();
        FOREIGN_SERVER.close();
        REJECTS_GATEWAY.close();
    }

    @Test
    void shouldReachTheServiceOverMtlsPresentingTheGatewayCertificate() {
        anonymousClient().post().uri("/api/v1/auth/login").exchange().expectStatus().isOk();

        assertThat(MS01.clientSubjects()).singleElement().asString().contains("CN=api-gateway-test");
    }

    @Test
    void shouldReturn502WhenTheServiceCertificateIsNotFromTheTrustedCa() {
        String body = client().get().uri("/api/v1/campus/edificios").exchange()
                .expectStatus().isEqualTo(502)
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 502, "SERVICIO_NO_DISPONIBLE", "Servicio temporalmente no disponible.");
        assertThat(FOREIGN_SERVER.clientSubjects()).isEmpty();
    }

    @Test
    void shouldReturn502WhenTheServiceRejectsTheGatewayCertificate() {
        String body = client().get().uri("/api/v1/alerts/activas").exchange()
                .expectStatus().isEqualTo(502)
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 502, "SERVICIO_NO_DISPONIBLE", "Servicio temporalmente no disponible.");
        assertThat(REJECTS_GATEWAY.clientSubjects()).isEmpty();
    }
}
