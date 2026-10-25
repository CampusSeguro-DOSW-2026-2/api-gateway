package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import static edu.eci.dosw.campusseguro.gateway.gateway_service.support.ErrorResponseAssertions.assertGatewayError;
import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdProvider;
import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.ExchangeAttributeCorrelationIdProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/** Contrato de los errores del GW (GW-C §4, RN-GW-14, CA-GW-05-01 y CA-GW-05-05). */
@ExtendWith(OutputCaptureExtension.class)
class GatewayErrorContractTest {

    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private final ErrorResponseWriter writer =
            new ErrorResponseWriter(JsonMapper.builder().build(), new ExchangeAttributeCorrelationIdProvider());
    private final GatewayErrorWebExceptionHandler handler = new GatewayErrorWebExceptionHandler(List.of(
            new GatewayExceptionErrorResolver(), new RouteNotFoundErrorResolver(), new DownstreamFailureErrorResolver()),
            writer);

    private final MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users"));

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "VALIDATION          | 400 | VALIDACION             | Solicitud inválida.",
        "UNAUTHORIZED        | 401 | NO_AUTENTICADO         | Token ausente, inválido o expirado.",
        "FORBIDDEN           | 403 | PROHIBIDO              | Acceso denegado por la política perimetral.",
        "NOT_FOUND           | 404 | NO_ENCONTRADO          | Ruta no encontrada.",
        "INTERNAL_ERROR      | 500 | ERROR_INTERNO          | Error interno del servidor.",
        "SERVICE_UNAVAILABLE | 502 | SERVICIO_NO_DISPONIBLE | Servicio temporalmente no disponible.",
        "TIMEOUT             | 504 | TIEMPO_AGOTADO         | El servicio de destino no respondió a tiempo."
    })
    void shouldSerializeEveryGatewayErrorWithExactlyTheFiveFields(GatewayError error, int status, String codigo, String mensaje) {
        StepVerifier.create(writer.write(exchange, error)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(status);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertGatewayError(exchange.getResponse().getBodyAsString().block(), status, codigo, mensaje);
    }

    @Test
    void shouldCoverTheWholeCatalogOfGatewayErrors() {
        assertThat(GatewayError.values()).hasSize(7);
    }

    @Test
    void shouldReturnUnauthorizedWithTheContextCorrelationIdWhenAFilterRejects() {
        exchange.getAttributes().put(CorrelationIdProvider.ATTRIBUTE, CORRELATION_ID);

        StepVerifier.create(handler.handle(exchange, new GatewayException(GatewayError.UNAUTHORIZED))).verifyComplete();

        String body = exchange.getResponse().getBodyAsString().block();
        assertGatewayError(body, 401, "NO_AUTENTICADO", "Token ausente, inválido o expirado.");
        assertThat(body).contains("\"correlation_id\":\"" + CORRELATION_ID + "\"");
    }

    @Test
    void shouldHideTheStackTraceFromTheClientButLogItWithTheCorrelation(CapturedOutput output) {
        exchange.getAttributes().put(CorrelationIdProvider.ATTRIBUTE, CORRELATION_ID);

        StepVerifier.create(handler.handle(exchange, new IllegalStateException("detalle-interno-secreto")))
                .verifyComplete();

        String body = exchange.getResponse().getBodyAsString().block();
        assertGatewayError(body, 500, "ERROR_INTERNO", "Error interno del servidor.");
        assertThat(body).doesNotContain("detalle-interno-secreto", "IllegalStateException");
        assertThat(output).contains("correlation_id=" + CORRELATION_ID)
                .contains("java.lang.IllegalStateException: detalle-interno-secreto")
                .contains("\tat ");
    }
}
