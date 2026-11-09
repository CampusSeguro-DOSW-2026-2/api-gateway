package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdProvider;
import edu.eci.dosw.campusseguro.gateway.gateway_service.routing.RouteConfig;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Paso 7 del pipeline (RF-GW-05): las respuestas 2xx/3xx del MS pasan sin leer el cuerpo; un error
 * 4xx/5xx se propaga sin cambios solo si cumple {@link DownstreamErrorValidator}. Si no, el GW
 * responde {@link GatewayError#SERVICE_UNAVAILABLE} y registra el motivo, nunca el cuerpo.
 */
@Component
public class DownstreamErrorValidationFilter implements GlobalFilter, Ordered {

    /** Tamaño máximo del cuerpo de error que se inspecciona; uno mayor se considera no conforme. */
    static final int MAX_ERROR_BODY_BYTES = 64 * 1024;

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorValidationFilter.class);

    private final DownstreamErrorValidator validator;
    private final ErrorResponseWriter writer;
    private final CorrelationIdProvider correlationIdProvider;

    public DownstreamErrorValidationFilter(DownstreamErrorValidator validator, ErrorResponseWriter writer,
            CorrelationIdProvider correlationIdProvider) {
        this.validator = validator;
        this.writer = writer;
        this.correlationIdProvider = correlationIdProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse validated = new ValidatingResponse(exchange);
        return chain.filter(exchange.mutate().response(validated).build());
    }

    /** Se ejecuta antes que el filtro que escribe la respuesta del MS, para envolverla. */
    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    private final class ValidatingResponse extends ServerHttpResponseDecorator {

        private final ServerWebExchange exchange;

        ValidatingResponse(ServerWebExchange exchange) {
            super(exchange.getResponse());
            this.exchange = exchange;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            HttpStatusCode status = getStatusCode();
            if (status == null || !status.isError()) {
                return super.writeWith(body);
            }
            // DataBufferLimitException solo la emite join(), al superar el límite de tamaño.
            return DataBufferUtils.join(body, MAX_ERROR_BODY_BYTES)
                    .map(DownstreamErrorValidationFilter::toBytes)
                    .defaultIfEmpty(new byte[0])
                    .flatMap(bytes -> validator
                            .violation(status.value(), bytes, correlationIdProvider.correlationId(exchange))
                            .map(violation -> replaceWithBadGateway(status, violation))
                            .orElseGet(() -> super.writeWith(Mono.just(bufferFactory().wrap(bytes)))))
                    .onErrorResume(DataBufferLimitException.class,
                            e -> replaceWithBadGateway(status, "cuerpo excede el límite"));
        }

        private Mono<Void> replaceWithBadGateway(HttpStatusCode status, String violation) {
            log.warn("Error downstream no conforme: {} status={} upstream={} correlation_id={}",
                    violation, status.value(), RouteConfig.upstreamOf(exchange), correlationIdProvider.correlationId(exchange));
            getDelegate().getHeaders().clear();
            return writer.write(exchange, getDelegate(), GatewayError.SERVICE_UNAVAILABLE);
        }
    }

    private static byte[] toBytes(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}
