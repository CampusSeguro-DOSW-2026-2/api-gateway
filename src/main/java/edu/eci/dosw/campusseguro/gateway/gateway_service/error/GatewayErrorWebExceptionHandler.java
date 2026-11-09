package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Manejador global: toda excepción sale como {@link ErrorResponse}, nunca con el cuerpo por defecto
 * de Spring ni con detalles internos. Lo no resuelto es {@link GatewayError#INTERNAL_ERROR}.
 */
@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    private final List<GatewayErrorResolver> resolvers;
    private final ErrorResponseWriter writer;

    public GatewayErrorWebExceptionHandler(List<GatewayErrorResolver> resolvers, ErrorResponseWriter writer) {
        this.resolvers = resolvers;
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            log.warn("Error con la respuesta ya iniciada; se cierra la conexión correlation_id={}: {}",
                    correlationIdOf(exchange), exception.toString());
            return Mono.error(exception);
        }
        GatewayError error = resolvers.stream()
                .flatMap(resolver -> resolver.resolve(exception).stream())
                .findFirst()
                .orElse(GatewayError.INTERNAL_ERROR);
        logError(exchange, error, exception);
        return writer.write(exchange, error);
    }

    private static void logError(ServerWebExchange exchange, GatewayError error, Throwable exception) {
        if (error == GatewayError.INTERNAL_ERROR) {
            log.error("Excepción no controlada en {} {} correlation_id={}", exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(), correlationIdOf(exchange), exception);
        } else if (error.status().is5xxServerError()) {
            log.warn("{} en {} {} correlation_id={}: {}", error.codigo(), exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(), correlationIdOf(exchange), exception.toString());
        }
    }

    private static Object correlationIdOf(ServerWebExchange exchange) {
        return exchange.getAttribute(CorrelationIdProvider.ATTRIBUTE);
    }
}
