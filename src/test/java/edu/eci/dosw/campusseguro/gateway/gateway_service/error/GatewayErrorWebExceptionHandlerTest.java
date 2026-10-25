package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdProvider;
import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.ExchangeAttributeCorrelationIdProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

class GatewayErrorWebExceptionHandlerTest {

    private final GatewayErrorWebExceptionHandler handler = new GatewayErrorWebExceptionHandler(
            List.of(new RouteNotFoundErrorResolver(), new DownstreamFailureErrorResolver()),
            new ErrorResponseWriter(JsonMapper.builder().build(), new ExchangeAttributeCorrelationIdProvider()));

    private final MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users"));

    @Test
    void unresolvedExceptionIsAGenericInternalError() {
        StepVerifier.create(handler.handle(exchange, new IllegalStateException("detalle interno"))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertGatewayError(exchange.getResponse().getBodyAsString().block(), 500, "ERROR_INTERNO",
                "Error interno del servidor.");
    }

    @Test
    void firstMatchingResolverWins() {
        StepVerifier.create(handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void correlationIdOfTheExchangeIsReused() {
        String correlationId = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
        exchange.getAttributes().put(CorrelationIdProvider.ATTRIBUTE, correlationId);

        StepVerifier.create(handler.handle(exchange, new IllegalStateException())).verifyComplete();

        assertThat(exchange.getResponse().getBodyAsString().block()).contains(correlationId);
    }

    @Test
    void committedResponsePropagatesTheError() {
        exchange.getResponse().setComplete().block();
        IllegalStateException failure = new IllegalStateException();

        StepVerifier.create(handler.handle(exchange, failure)).verifyErrorMatches(failure::equals);
    }
}
