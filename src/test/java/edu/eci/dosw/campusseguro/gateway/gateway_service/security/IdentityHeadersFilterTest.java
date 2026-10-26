package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** CA-GW-03-01 a CA-GW-03-05 sobre las cabeceras que salen hacia el MS. */
class IdentityHeadersFilterTest {

    private static final String USER_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";

    private final IdentityHeadersFilter filter = new IdentityHeadersFilter();

    private HttpHeaders forwardedHeaders(MockServerHttpRequest.BaseBuilder<?> request, AuthenticatedUser user) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        if (user != null) {
            exchange.getAttributes().put(AuthenticatedUser.ATTRIBUTE, user);
        }
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        StepVerifier.create(filter.filter(exchange, next -> {
            forwarded.set(next.getRequest().getHeaders());
            return Mono.empty();
        })).verifyComplete();
        return forwarded.get();
    }

    private static void assertAbsent(HttpHeaders headers, String... names) {
        for (String name : names) {
            assertThat(headers.containsHeader(name)).as(name).isFalse();
        }
    }

    private static MockServerHttpRequest.BaseBuilder<?> spoofedRequest() {
        return MockServerHttpRequest.get("/api/v1/users/me")
                .header("x-user-id", "11111111-1111-4111-8111-111111111111")
                .header("X-USER-ROLE", "ADMIN")
                .header("X-User-Role", "AUDITOR")
                .header("x-service-name", "ms08")
                .header("X-Service-Key", "clave-robada")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .header("X-Correlation-ID", "c19b472d-7a2a-4d3b-a12b-abcdef123456")
                .header(HttpHeaders.ACCEPT, "application/json");
    }

    @Test
    void shouldInjectIdentityFromTheTokenInTokenOrder() {
        HttpHeaders headers = forwardedHeaders(MockServerHttpRequest.get("/api/v1/users/me"),
                new AuthenticatedUser(USER_ID, List.of("BRIGADISTA", "ESTUDIANTE")));

        assertThat(headers.get("X-User-Id")).containsExactly(USER_ID);
        assertThat(headers.get("X-User-Role")).containsExactly("BRIGADISTA,ESTUDIANTE");
    }

    @Test
    void shouldReplaceSpoofedIdentityWithASingleCanonicalValueFromTheToken() {
        HttpHeaders headers = forwardedHeaders(spoofedRequest(), new AuthenticatedUser(USER_ID, List.of("ESTUDIANTE")));

        assertThat(headers.get("X-User-Id")).containsExactly(USER_ID);
        assertThat(headers.get("X-User-Role")).containsExactly("ESTUDIANTE");
    }

    @Test
    void shouldNotForwardCredentialsNorServiceNameOnProtectedRoutes() {
        HttpHeaders headers = forwardedHeaders(spoofedRequest(), new AuthenticatedUser(USER_ID, List.of("ESTUDIANTE")));

        assertAbsent(headers, HttpHeaders.AUTHORIZATION, "X-Service-Key", "X-Service-Name");
    }

    @Test
    void shouldNotInjectNorForwardIdentityOnPublicRoutes() {
        HttpHeaders headers = forwardedHeaders(spoofedRequest(), null);

        assertAbsent(headers, "X-User-Id", "X-User-Role", "X-Service-Name",
                HttpHeaders.AUTHORIZATION, "X-Service-Key");
    }

    @Test
    void shouldRemoveTheEightHopByHopHeaders() {
        HttpHeaders headers = forwardedHeaders(MockServerHttpRequest.post("/api/v1/auth/login")
                .header("Connection", "keep-alive")
                .header("keep-alive", "timeout=5")
                .header("Proxy-Authenticate", "Basic")
                .header("Proxy-Authorization", "Basic dXNlcjpwYXNz")
                .header("TE", "trailers")
                .header("Trailer", "Expires")
                .header("Transfer-Encoding", "chunked")
                .header("Upgrade", "websocket"), null);

        assertAbsent(headers, "Connection", "Keep-Alive", "Proxy-Authenticate",
                "Proxy-Authorization", "TE", "Trailer", "Transfer-Encoding", "Upgrade");
    }

    @Test
    void shouldKeepCorrelationAndOtherClientHeaders() {
        HttpHeaders headers = forwardedHeaders(spoofedRequest(), null);

        assertThat(headers.get("X-Correlation-ID")).containsExactly("c19b472d-7a2a-4d3b-a12b-abcdef123456");
        assertThat(headers.get(HttpHeaders.ACCEPT)).containsExactly("application/json");
    }

    @Test
    void shouldInjectTheServiceNameOfTheAuthenticatedCallerInsteadOfTheSpoofedOne() {
        MockServerWebExchange exchange = MockServerWebExchange.from(spoofedRequest());
        exchange.getAttributes().put(CallingService.ATTRIBUTE, new CallingService("ms04"));
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, next -> {
            forwarded.set(next.getRequest().getHeaders());
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().get("X-Service-Name")).containsExactly("ms04");
        assertAbsent(forwarded.get(), "X-Service-Key");
    }
}
