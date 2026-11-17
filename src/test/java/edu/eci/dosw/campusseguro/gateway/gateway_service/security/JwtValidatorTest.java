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
import edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts.SessionCutSnapshot;
import edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts.SessionCutStore;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** CA-GW-02-01, CA-GW-02-03, CA-GW-02-04 y tolerancia de reloj. */
class JwtValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");

    private final SessionCutStore sessionCuts = new SessionCutStore();

    private final JwtValidator validator = new JwtValidator(
            new JwtProperties(TestJwt.publicKeyPem(), TestJwt.ISSUER, TestJwt.AUDIENCE, Duration.ofSeconds(30),
                    Duration.ofSeconds(3600)),
            Clock.fixed(NOW, ZoneOffset.UTC), sessionCuts);

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

    /**
     * Un token válido entrega el usuario, sus roles y sus permisos en el orden del token
     */
    @Test
    void shouldAcceptValidTokenAndReturnItsIdentityInTokenOrder() {
        AuthenticatedUser user = validator.validate(TestJwt.sign(claims().build()));

        assertThat(user.userId()).isEqualTo(TestJwt.USER_ID);
        assertThat(user.roles()).containsExactly("BRIGADISTA", "ESTUDIANTE");
        assertThat(user.permissions()).containsExactly("ms01:profile:read", "ms04:checkin:write");
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

    /**
     * Un nombre de rol que rompe el formato guardado en MS01 se rechaza
     */
    @ParameterizedTest
    @ValueSource(strings = {"E", "1ROL", "_ROL", "ROL-GUION", "ROL CON ESPACIO", "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345"})
    void shouldRejectRoleNamesThatBreakTheStoredFormat(String role) {
        assertRejected(TestJwt.sign(claims().claim("roles", List.of("ESTUDIANTE", role)).build()));
    }

    /**
     * Cualquier nombre de rol con el formato guardado en MS01 se acepta, no solo los iniciales
     */
    @ParameterizedTest
    @ValueSource(strings = {"AB", "ROL_NUEVO_2", "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234"})
    void shouldAcceptAnyRoleNameWithTheStoredFormat(String role) {
        AuthenticatedUser user = validator.validate(TestJwt.sign(claims().claim("roles", List.of(role)).build()));

        assertThat(user.roles()).containsExactly(role);
    }

    /**
     * Un token sin el claim permissions se rechaza
     */
    @Test
    void shouldRejectTokenWithoutPermissions() {
        assertRejected(TestJwt.sign(claims().claim("permissions", null).build()));
    }

    /**
     * Un token con la lista de permisos vacía se rechaza
     */
    @Test
    void shouldRejectEmptyPermissions() {
        assertRejected(TestJwt.sign(claims().claim("permissions", List.of()).build()));
    }

    /**
     * Un token cuyo claim permissions no es una lista se rechaza
     */
    @Test
    void shouldRejectPermissionsThatAreNotAList() {
        assertRejected(TestJwt.sign(claims().claim("permissions", "ms01:profile:read").build()));
    }

    /**
     * Un permiso con formato inválido se rechaza aunque los demás sean válidos
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms1:profile:read", "MS01:profile:read", "ms01:profile", "ms01:profile:read:more",
            "ms01:Profile:read", "ms01:1profile:read", "ms01:profile:read-all", "ms01::read", "ms01:profile:",
            "ms01: profile:read", ""})
    void shouldRejectPermissionsWithInvalidFormat(String permission) {
        assertRejected(TestJwt.sign(claims().claim("permissions", List.of("ms01:profile:read", permission)).build()));
    }

    /**
     * Una entrada de permissions que no es texto se rechaza
     */
    @Test
    void shouldRejectPermissionsWithANonTextEntry() {
        assertRejected(TestJwt.sign(claims().claim("permissions", List.of("ms01:profile:read", 7)).build()));
    }

    /**
     * Sin cortes de sesión un token válido se acepta
     */
    @Test
    void shouldAcceptTokenIssuedBeforeNoCut() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(), Map.of()));

        assertThat(validator.validate(TestJwt.sign(claims().build()))).isNotNull();
    }

    /**
     * Un token emitido antes del corte del usuario se rechaza
     */
    @Test
    void shouldRejectTokenIssuedBeforeTheCutOfTheUser() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(TestJwt.USER_ID, NOW.minusSeconds(30)), Map.of()));

        assertRejected(TestJwt.sign(claims().build()));
    }

    /**
     * Un token emitido en el mismo segundo del corte se acepta
     */
    @Test
    void shouldAcceptTokenIssuedExactlyAtTheCutOfTheUser() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(TestJwt.USER_ID, NOW.minusSeconds(60)), Map.of()));

        assertThat(validator.validate(TestJwt.sign(claims().build()))).isNotNull();
    }

    /**
     * Un token emitido después del corte se acepta
     */
    @Test
    void shouldAcceptTokenIssuedAfterTheCutOfTheUser() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(TestJwt.USER_ID, NOW.minusSeconds(90)), Map.of()));

        assertThat(validator.validate(TestJwt.sign(claims().build()))).isNotNull();
    }

    /**
     * Un token emitido antes del corte de alguno de sus roles se rechaza
     */
    @Test
    void shouldRejectTokenIssuedBeforeTheCutOfOneOfItsRoles() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(), Map.of("BRIGADISTA", NOW.minusSeconds(10))));

        assertRejected(TestJwt.sign(claims().build()));
    }

    /**
     * El corte de un rol que el token no tiene no lo afecta
     */
    @Test
    void shouldIgnoreTheCutOfARoleTheTokenDoesNotHave() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(), Map.of("PROFESOR", NOW.minusSeconds(10))));

        assertThat(validator.validate(TestJwt.sign(claims().build()))).isNotNull();
    }

    /**
     * El corte de otro usuario no afecta al token
     */
    @Test
    void shouldIgnoreTheCutOfAnotherUser() {
        sessionCuts.replace(new SessionCutSnapshot(
                Map.of("11111111-2222-4333-8444-555555555555", NOW.minusSeconds(10)), Map.of()));

        assertThat(validator.validate(TestJwt.sign(claims().build()))).isNotNull();
    }

    /**
     * Cuando una instantánea nueva ya no trae el corte, el token vuelve a aceptarse
     */
    @Test
    void shouldStopRejectingWhenANewSnapshotNoLongerHasTheCut() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(TestJwt.USER_ID, NOW.minusSeconds(30)), Map.of()));
        String token = TestJwt.sign(claims().build());
        assertRejected(token);

        sessionCuts.replace(SessionCutSnapshot.EMPTY);

        assertThat(validator.validate(token)).isNotNull();
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
