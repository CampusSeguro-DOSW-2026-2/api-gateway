package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.TestJwt;
import java.time.Duration;
import java.util.List;
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

    private final AccessControlFilter filter = new AccessControlFilter(
            new AccessPolicyResolver(new AccessPolicyProperties(
                    List.of(new AccessPolicyProperties.Endpoint(HttpMethod.POST, "/api/v1/auth/login")),
                    List.of(new AccessPolicyProperties.Endpoint(HttpMethod.POST, "/api/v1/audit/events")))),
            new JwtValidator(new JwtProperties(TestJwt.publicKeyPem(), TestJwt.ISSUER, TestJwt.AUDIENCE,
                    Duration.ofSeconds(30), Duration.ofSeconds(3600))));

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

    @Test
    void shouldStoreTheAuthenticatedUserForAValidBearerToken() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "bearer " + TestJwt.validToken()));

        run(exchange).verifyComplete();

        assertThat(forwarded).isTrue();
        assertThat((AuthenticatedUser) exchange.getAttribute(AuthenticatedUser.ATTRIBUTE))
                .isEqualTo(new AuthenticatedUser(TestJwt.USER_ID, TestJwt.ROLES));
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
    void shouldRejectInternalRouteUntilServiceKeysExist() {
        assertRejectedWith(MockServerHttpRequest.post("/api/v1/audit/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.validToken()), GatewayError.UNAUTHORIZED);
    }
}
