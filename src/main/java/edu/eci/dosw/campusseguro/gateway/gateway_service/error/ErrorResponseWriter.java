package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdProvider;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** Escribe en la respuesta un error del GW con el {@link ErrorResponse} estándar. */
@Component
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;
    private final CorrelationIdProvider correlationIdProvider;

    public ErrorResponseWriter(JsonMapper jsonMapper, CorrelationIdProvider correlationIdProvider) {
        this.jsonMapper = jsonMapper;
        this.correlationIdProvider = correlationIdProvider;
    }

    public Mono<Void> write(ServerWebExchange exchange, GatewayError error) {
        return write(exchange, exchange.getResponse(), error);
    }

    /** Escribe el error en {@code response} (p. ej. la respuesta original bajo un decorador). */
    public Mono<Void> write(ServerWebExchange exchange, ServerHttpResponse response, GatewayError error) {
        ErrorResponse body = new ErrorResponse(error.status().value(), error.codigo(), error.mensaje(), List.of(),
                correlationIdProvider.correlationId(exchange));
        response.setStatusCode(error.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(body));
        return response.writeWith(Mono.just(buffer));
    }
}
