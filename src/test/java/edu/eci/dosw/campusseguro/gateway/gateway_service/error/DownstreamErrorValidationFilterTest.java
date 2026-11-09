package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdProvider;
import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.ExchangeAttributeCorrelationIdProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class DownstreamErrorValidationFilterTest {

    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String VALID_409 = "{\"status\":409,\"codigo\":\"CONFLICTO\",\"mensaje\":\"El correo ya está registrado.\","
            + "\"errores\":[],\"correlation_id\":\"" + CORRELATION_ID + "\"}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final CorrelationIdProvider correlationIdProvider = new ExchangeAttributeCorrelationIdProvider();
    private final DownstreamErrorValidationFilter filter = new DownstreamErrorValidationFilter(
            new DownstreamErrorValidator(jsonMapper), new ErrorResponseWriter(jsonMapper, correlationIdProvider),
            correlationIdProvider);

    private MockServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users"));
        exchange.getAttributes().put(CorrelationIdProvider.ATTRIBUTE, CORRELATION_ID);
    }

    /** Simula lo que escribe el MS a través del filtro (Netty entrega el cuerpo como Flux). */
    private void msResponds(HttpStatus status, String body) {
        Mono<Void> result = filter.filter(exchange, (ServerWebExchange forwarded) -> {
            forwarded.getResponse().setStatusCode(status);
            forwarded.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            forwarded.getResponse().getHeaders().add("X-Interno", "ms01-identity-and-access");
            return forwarded.getResponse().writeWith(Flux.just(
                    forwarded.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
        });
        StepVerifier.create(result).verifyComplete();
    }

    private String responseBody() {
        return exchange.getResponse().getBodyAsString().block();
    }

    @Test
    void shouldNotInspectSuccessfulResponses() {
        msResponds(HttpStatus.OK, "esto no es JSON");

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseBody()).isEqualTo("esto no es JSON");
    }

    @Test
    void shouldPropagateConformingErrorUnchanged() {
        msResponds(HttpStatus.CONFLICT, VALID_409);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(responseBody()).isEqualTo(VALID_409);
    }

    @Test
    void shouldReplaceNonConformingErrorWith502AndLogOnlyTheReason(CapturedOutput output) {
        msResponds(HttpStatus.METHOD_NOT_ALLOWED,
                "{\"timestamp\":\"2026-10-25T10:00:00Z\",\"status\":405,\"error\":\"Method Not Allowed\",\"path\":\"/api/v1/users\"}");

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exchange.getResponse().getHeaders().get("X-Interno")).isNull();
        assertGatewayError(responseBody(), 502, "SERVICIO_NO_DISPONIBLE", "Servicio temporalmente no disponible.");
        assertThat(output).contains("campo faltante: codigo").doesNotContain("Method Not Allowed");
    }

    @Test
    void shouldReplaceOversizedErrorWith502(CapturedOutput output) {
        msResponds(HttpStatus.INTERNAL_SERVER_ERROR, "x".repeat(DownstreamErrorValidationFilter.MAX_ERROR_BODY_BYTES + 1));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(output).contains("cuerpo excede el límite");
    }
}
