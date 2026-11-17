package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts.SessionCutSnapshot;
import edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts.SessionCutStore;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Paso 4 del pipeline: política de la ruta + extracción del Bearer (RF-GW-02, A1, A3 y A4). */
class AccessControlFilterTest {

    private final SessionCutStore sessionCuts = new SessionCutStore();

    private final AccessControlFilter filter = new AccessControlFilter(
            new AccessPolicyResolver(new AccessPolicyProperties(
                    List.of(new AccessPolicyProperties.Endpoint(HttpMethod.POST, "/api/v1/auth/login")),
                    List.of(new AccessPolicyProperties.Endpoint(HttpMethod.POST, "/api/v1/audit/events")))),
            new JwtValidator(new JwtProperties(TestJwt.publicKeyPem(), TestJwt.ISSUER, TestJwt.AUDIENCE,
                    Duration.ofSeconds(30), Duration.ofSeconds(3600)), sessionCuts),
            new ServiceKeyAuthenticator(new ServiceKeyProperties(Map.of("ms04", MS04_KEY))));

    private static final String MS04_KEY = "clave-de-servicio-ms04-de-al-menos-32-caracteres";

    private final AtomicBoolean forwarded = new AtomicBoolean();

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    private StepVerifier.FirstStep<Void> run(MockServerWebExchange exchange) {
        return StepVerifier.create(filter.filter(exchange, e -> Mono.fromRunnable(() -> forwarded.set(true))));
    }

    private void assertRejectedWith(MockServerHttpRequest.BaseBuilder<?> request, GatewayError expected) {
        run(exchange(request)).verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOfSatisfying(GatewayException.class, e -> assertThat(e.error()).isEqualTo(expected)));
        assertThat(forwarded).isFalse();
    }

    @Test
    void shouldForwardPublicRouteWithoutCredential() {
        run(exchange(MockServerHttpRequest.post("/api/v1/auth/login"))).verifyComplete();

        assertThat(forwarded).isTrue();
    }

    /**
     * Con un Bearer válido queda en el exchange el usuario con sus roles y permisos
     */
    @Test
    void shouldStoreTheAuthenticatedUserForAValidBearerToken() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "bearer " + TestJwt.validToken()));

        run(exchange).verifyComplete();

        assertThat(forwarded).isTrue();
        assertThat((AuthenticatedUser) exchange.getAttribute(AuthenticatedUser.ATTRIBUTE))
                .isEqualTo(new AuthenticatedUser(TestJwt.USER_ID, TestJwt.ROLES, TestJwt.PERMISSIONS));
    }

    /**
     * Un token anterior a un corte de sesión vigente se rechaza con 401
     */
    @Test
    void shouldRejectAValidTokenIssuedBeforeAnActiveSessionCut() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(), Map.of("ESTUDIANTE", Instant.now().plusSeconds(5))));

        assertRejectedWith(MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validToken()), GatewayError.UNAUTHORIZED);
    }

    /**
     * Los cortes de sesión no afectan a las llamadas internas con clave de servicio
     */
    @Test
    void shouldNotApplySessionCutsToAnInternalCallWithAServiceKey() {
        sessionCuts.replace(new SessionCutSnapshot(Map.of(STUDENT_ID, Instant.now().plusSeconds(5)), Map.of()));

        run(exchange(MockServerHttpRequest.post("/api/v1/audit/events")
                .header("X-Service-Key", MS04_KEY)
                .header("X-User-Id", STUDENT_ID)
                .header("X-User-Role", "ESTUDIANTE")
                .header("X-User-Permission", "ms04:checkin:write"))).verifyComplete();

        assertThat(forwarded).isTrue();
    }

    @Test
    void shouldRejectProtectedRouteWithoutAuthorization() {
        assertRejectedWith(MockServerHttpRequest.get("/api/v1/users/me"), GatewayError.UNAUTHORIZED);
    }

    @Test
    void shouldRejectSchemeOtherThanBearer() {
        assertRejectedWith(MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"), GatewayError.UNAUTHORIZED);
    }

    @Test
    void shouldRejectMoreThanOneAuthorizationHeaderAsValidation() {
        assertRejectedWith(MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validToken(), "Bearer " + TestJwt.validToken()),
                GatewayError.VALIDATION);
    }

    @Test
    void shouldRejectInternalRouteWithAJwtInsteadOfAServiceKey() {
        assertRejectedWith(MockServerHttpRequest.post("/api/v1/audit/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validToken()), GatewayError.UNAUTHORIZED);
    }

    private static final String STUDENT_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";

    /**
     * Una llamada interna conserva la identidad completa del usuario por el que actúa
     */
    @Test
    void shouldAuthenticateAnInternalCallAndKeepTheUserItActsFor() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/audit/events")
                .header("X-Service-Key", MS04_KEY)
                .header("X-User-Id", STUDENT_ID)
                .header("X-User-Role", "BRIGADISTA,ESTUDIANTE")
                .header("X-User-Permission", "ms04:checkin:write,ms07:content:read"));

        run(exchange).verifyComplete();

        assertThat(forwarded).isTrue();
        assertThat((CallingService) exchange.getAttribute(CallingService.ATTRIBUTE)).isEqualTo(new CallingService("ms04"));
        assertThat((AuthenticatedUser) exchange.getAttribute(AuthenticatedUser.ATTRIBUTE))
                .isEqualTo(new AuthenticatedUser(STUDENT_ID, List.of("BRIGADISTA", "ESTUDIANTE"),
                        List.of("ms04:checkin:write", "ms07:content:read")));
    }

    @Test
    void shouldAuthenticateAnInternalCallWithoutUser() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/audit/events")
                .header("X-Service-Key", MS04_KEY));

        run(exchange).verifyComplete();

        assertThat((CallingService) exchange.getAttribute(CallingService.ATTRIBUTE)).isEqualTo(new CallingService("ms04"));
        assertThat((Object) exchange.getAttribute(AuthenticatedUser.ATTRIBUTE)).isNull();
    }

    @Test
    void shouldAcceptAServiceKeyInsteadOfAJwtOnProtectedRoutes() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me")
                .header("X-Service-Key", MS04_KEY));

        run(exchange).verifyComplete();

        assertThat(forwarded).isTrue();
    }

    @Test
    void shouldRejectAnInvalidServiceKey() {
        assertRejectedWith(MockServerHttpRequest.post("/api/v1/audit/events")
                .header("X-Service-Key", "clave-robada-de-mas-de-32-caracteres-xxxx"), GatewayError.UNAUTHORIZED);
    }

    @Test
    void shouldRejectAnInternalRouteWithoutServiceKey() {
        assertRejectedWith(MockServerHttpRequest.post("/api/v1/audit/events"), GatewayError.UNAUTHORIZED);
    }

    @Test
    void shouldRejectAuthorizationAndServiceKeyTogether() {
        assertRejectedWith(MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validToken())
                .header("X-Service-Key", MS04_KEY), GatewayError.VALIDATION);
    }

    @Test
    void shouldRejectMoreThanOneServiceKey() {
        assertRejectedWith(MockServerHttpRequest.post("/api/v1/audit/events")
                .header("X-Service-Key", MS04_KEY, MS04_KEY), GatewayError.VALIDATION);
    }

    /**
     * Una identidad propagada incompleta o con formato inválido se rechaza con 400
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "X-User-Id={0} X-User-Role={1} X-User-Permission={2}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', nullValues = "NULL", value = {
        STUDENT_ID_LITERAL + " | NULL | NULL",
        STUDENT_ID_LITERAL + " | ESTUDIANTE | NULL",
        STUDENT_ID_LITERAL + " | NULL | ms04:checkin:write",
        "NULL | ESTUDIANTE | ms04:checkin:write",
        "NULL | NULL | ms04:checkin:write",
        "no-es-uuid | ESTUDIANTE | ms04:checkin:write",
        STUDENT_ID_LITERAL + " | estudiante | ms04:checkin:write",
        STUDENT_ID_LITERAL + " | 'BRIGADISTA, ESTUDIANTE' | ms04:checkin:write",
        STUDENT_ID_LITERAL + " | 'ESTUDIANTE,' | ms04:checkin:write",
        STUDENT_ID_LITERAL + " | ESTUDIANTE | MS04:checkin:write",
        STUDENT_ID_LITERAL + " | ESTUDIANTE | ms04:checkin",
        STUDENT_ID_LITERAL + " | ESTUDIANTE | 'ms04:checkin:write, ms07:content:read'",
        STUDENT_ID_LITERAL + " | ESTUDIANTE | 'ms04:checkin:write,'"
    })
    void shouldRejectAnIncompleteOrMalformedPropagatedIdentity(String userId, String roles, String permissions) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post("/api/v1/audit/events")
                .header("X-Service-Key", MS04_KEY);
        if (userId != null) {
            request.header("X-User-Id", userId);
        }
        if (roles != null) {
            request.header("X-User-Role", roles);
        }
        if (permissions != null) {
            request.header("X-User-Permission", permissions);
        }

        assertRejectedWith(request, GatewayError.VALIDATION);
    }

    /**
     * Una cabecera X-User-Permission repetida en una llamada interna se rechaza con 400
     */
    @Test
    void shouldRejectARepeatedPermissionHeaderInAnInternalCall() {
        assertRejectedWith(MockServerHttpRequest.post("/api/v1/audit/events")
                .header("X-Service-Key", MS04_KEY)
                .header("X-User-Id", STUDENT_ID)
                .header("X-User-Role", "ESTUDIANTE")
                .header("X-User-Permission", "ms04:checkin:write", "ms07:content:read"), GatewayError.VALIDATION);
    }

    private static final String STUDENT_ID_LITERAL = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
}
