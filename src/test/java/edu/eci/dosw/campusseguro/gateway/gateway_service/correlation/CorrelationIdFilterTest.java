package edu.eci.dosw.campusseguro.gateway.gateway_service.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationIdFilterTest {

    private static final String VALID_V4 = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final AtomicReference<String> contextValue = new AtomicReference<>();

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        Mono<Void> result = filter.filter(exchange, next -> Mono.deferContextual(context -> {
            forwarded.set(next);
            contextValue.set(context.get(CorrelationIdFilter.CONTEXT_KEY));
            return next.getResponse().setComplete();
        }));
        StepVerifier.create(result).verifyComplete();
        return exchange;
    }

    @Test
    void shouldKeepValidV4Everywhere() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/users").header("X-Correlation-ID", VALID_V4));

        assertThat(forwarded.get().getRequest().getHeaders().get("X-Correlation-ID")).containsExactly(VALID_V4);
        assertThat(exchange.getResponse().getHeaders().get("X-Correlation-ID")).containsExactly(VALID_V4);
        assertThat((String) exchange.getAttribute(CorrelationIdProvider.ATTRIBUTE)).isEqualTo(VALID_V4);
        assertThat(contextValue.get()).isEqualTo(VALID_V4);
    }

    @Test
    void shouldReadHeaderNameCaseInsensitively() {
        run(MockServerHttpRequest.get("/api/v1/users").header("x-correlation-id", VALID_V4));

        assertThat(forwarded.get().getRequest().getHeaders().get("X-Correlation-ID")).containsExactly(VALID_V4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "texto-libre", "c19b472d-7a2a-1d3b-a12b-abcdef123456", "c19b472d-7a2a-4d3b-c12b-abcdef123456"})
    void shouldReplaceInvalidValueWithoutRejectingTheRequest(String value) {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/users").header("X-Correlation-ID", value));

        assertNewV4(exchange, value);
    }

    @Test
    void shouldGenerateNewV4WhenHeaderIsMissing() {
        assertNewV4(run(MockServerHttpRequest.get("/api/v1/users")), null);
    }

    @Test
    void shouldReplaceDuplicatedHeader() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/v1/users")
                .header("X-Correlation-ID", VALID_V4)
                .header("x-correlation-id", VALID_V4));

        assertNewV4(exchange, VALID_V4);
    }

    private void assertNewV4(MockServerWebExchange exchange, String original) {
        String generated = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        assertThat(generated).isNotEqualTo(original);
        assertThat(UUID.fromString(generated).version()).isEqualTo(4);
        assertThat(forwarded.get().getRequest().getHeaders().get("X-Correlation-ID")).containsExactly(generated);
        assertThat(contextValue.get()).isEqualTo(generated);
    }
}
