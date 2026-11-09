package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.DownstreamStub.RecordedRequest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** INT-GW-03, CA-GW-02-02, CA-GW-02-05 y CA-GW-02-06. */
class JwtIntegrationTest extends GatewayIntegrationTest {

    private static final DownstreamStub MS01 = DownstreamStub.start();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("MS01_URI", MS01::baseUrl);
    }

    @AfterAll
    static void stop() {
        MS01.close();
    }

    @BeforeEach
    void reset() {
        MS01.reset();
    }

    static Stream<Arguments> invalidTokens() {
        Instant now = Instant.now();
        String valid = TestJwt.validToken();
        return Stream.of(
                Arguments.of("expirado", TestJwt.sign(TestJwt.validClaims(now.minusSeconds(7200)).build())),
                Arguments.of("alterado", valid.substring(0, valid.length() - 4) + "AAAA"),
                Arguments.of("otra llave", TestJwt.sign(TestJwt.validClaims(now).build(),
                        TestJwt.newKeyPair(2048).getPrivate())),
                Arguments.of("iss incorrecto", TestJwt.sign(TestJwt.validClaims(now).issuer("otro").build())),
                Arguments.of("aud incorrecto", TestJwt.sign(TestJwt.validClaims(now).audience("otra").build())),
                Arguments.of("vigencia mayor", TestJwt.sign(TestJwt.validClaims(now)
                        .expirationTime(Date.from(now.plusSeconds(7200))).build())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    void shouldReturn401WithoutCallingTheMsForAnInvalidToken(String description, String token) {
        String body = anonymousClient().get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(String.class).returnResult().getResponseBody();

        assertGatewayError(body, 401, "NO_AUTENTICADO", "Token ausente, inválido o expirado.");
        assertThat(MS01.requests()).isEmpty();
    }

    @Test
    void shouldReturn401WithoutCallingTheMsWhenAuthorizationIsMissing() {
        anonymousClient().post().uri("/api/v1/auth/logout").exchange().expectStatus().isUnauthorized();
        anonymousClient().get().uri("/api/v1/auth/login").exchange().expectStatus().isUnauthorized();

        assertThat(MS01.requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/password/forgot",
        "/api/v1/auth/password/reset"})
    void shouldForwardThePublicMs01RoutesWithoutJwt(String path) {
        anonymousClient().post().uri(path).exchange().expectStatus().isOk();

        assertThat(MS01.requests()).extracting(RecordedRequest::uri).containsExactly(path);
    }

    @Test
    void shouldForwardAValidJwtWithoutAnyOtherCallToMs01() {
        client().get().uri("/api/v1/users/me").exchange().expectStatus().isOk();

        assertThat(MS01.requests()).extracting(RecordedRequest::uri).containsExactly("/api/v1/users/me");
    }

    @Test
    void shouldStillReturn404ForUnknownRouteWithoutJwt() {
        anonymousClient().get().uri("/api/v1/inexistente").exchange().expectStatus().isNotFound();
    }
}
