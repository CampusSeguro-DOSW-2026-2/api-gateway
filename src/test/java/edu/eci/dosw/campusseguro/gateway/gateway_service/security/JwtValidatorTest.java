package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** CA-GW-02-01, CA-GW-02-03, CA-GW-02-04 y tolerancia de reloj. */
class JwtValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");

    private final JwtValidator validator = new JwtValidator(
            new JwtProperties(TestJwt.publicKeyPem(), TestJwt.ISSUER, TestJwt.AUDIENCE, Duration.ofSeconds(30),
                    Duration.ofSeconds(3600)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static JWTClaimsSet.Builder claims() {
        return TestJwt.validClaims(NOW.minusSeconds(60));
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOfSatisfying(GatewayException.class,
                        e -> assertThat(e.error()).isEqualTo(GatewayError.UNAUTHORIZED));
    }

    private static String expiredSecondsAgo(long seconds) {
        Instant expiresAt = NOW.minusSeconds(seconds);
        return TestJwt.sign(claims().issueTime(Date.from(expiresAt.minusSeconds(3600)))
                .expirationTime(Date.from(expiresAt)).build());
    }

    @Test
    void shouldAcceptValidTokenAndReturnItsIdentityInTokenOrder() {
        AuthenticatedUser user = validator.validate(TestJwt.sign(claims().build()));

        assertThat(user.userId()).isEqualTo(TestJwt.USER_ID);
        assertThat(user.roles()).containsExactly("BRIGADISTA", "ESTUDIANTE");
    }

    @Test
    void shouldAcceptTokenExpiredWithinTheClockSkew() {
        assertThat(validator.validate(expiredSecondsAgo(20))).isNotNull();
    }

    @Test
    void shouldRejectTokenExpiredBeyondTheClockSkew() {
        assertRejected(expiredSecondsAgo(40));
    }

    @Test
    void shouldRejectTokenNotYetValid() {
        assertRejected(TestJwt.sign(claims().notBeforeTime(Date.from(NOW.plusSeconds(60))).build()));
    }

    @Test
    void shouldRejectTokenIssuedInTheFuture() {
        assertRejected(TestJwt.sign(TestJwt.validClaims(NOW.plusSeconds(120)).build()));
    }

    @Test
    void shouldRejectTokenSignedWithAnotherKey() {
        assertRejected(TestJwt.sign(claims().build(), TestJwt.newKeyPair(2048).getPrivate()));
    }

    @Test
    void shouldRejectTamperedToken() {
        String[] parts = TestJwt.sign(claims().build()).split("\\.");
        String forgedPayload = base64Url(claims().claim("roles", List.of("ADMIN")).build().toString());

        assertRejected(parts[0] + "." + forgedPayload + "." + parts[2]);
    }

    @Test
    void shouldRejectUnsignedTokenWithAlgNone() {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");

        assertRejected(header + "." + base64Url(claims().build().toString()) + ".");
    }

    @Test
    void shouldRejectSymmetricHs256Token() throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims().build());
        jwt.sign(new MACSigner("una-clave-simetrica-de-al-menos-32-bytes!!".getBytes(StandardCharsets.UTF_8)));

        assertRejected(jwt.serialize());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-es-un-jwt", "a.b.c"})
    void shouldRejectMalformedToken(String token) {
        assertRejected(token);
    }

    @Test
    void shouldRejectWrongIssuer() {
        assertRejected(TestJwt.sign(claims().issuer("otro-emisor").build()));
    }

    @Test
    void shouldRejectWrongAudience() {
        assertRejected(TestJwt.sign(claims().audience("otra-audiencia").build()));
    }

    @Test
    void shouldRejectSubjectThatIsNotAUuid() {
        assertRejected(TestJwt.sign(claims().subject("maria.perez").build()));
    }

    @Test
    void shouldRejectEmptyRoles() {
        assertRejected(TestJwt.sign(claims().claim("roles", List.of()).build()));
    }

    @Test
    void shouldRejectRolesWithInvalidFormat() {
        assertRejected(TestJwt.sign(claims().claim("roles", List.of("estudiante")).build()));
        assertRejected(TestJwt.sign(claims().claim("roles", "ESTUDIANTE").build()));
    }

    @Test
    void shouldRejectLifetimeLongerThanTheMaximum() {
        assertRejected(TestJwt.sign(claims().expirationTime(Date.from(NOW.minusSeconds(60).plusSeconds(3601))).build()));
    }

    @Test
    void shouldRejectTokenWithoutExpiration() {
        assertRejected(TestJwt.sign(claims().expirationTime(null).build()));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
