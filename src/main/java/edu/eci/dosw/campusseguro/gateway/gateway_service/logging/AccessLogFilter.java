package edu.eci.dosw.campusseguro.gateway.gateway_service.logging;

import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdProvider;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayErrorWebExceptionHandler;
import edu.eci.dosw.campusseguro.gateway.gateway_service.routing.RouteConfig;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Paso 8 del pipeline (RF-GW-07): un log de acceso por solicitud con los campos mínimos de los
 * Estándares §8. Corre justo después de la correlación y registra al terminar la respuesta, también
 * en errores y cancelaciones. Nunca registra cuerpos, query params ni cabeceras.
 *
 * <p>Los errores de la cadena se resuelven aquí con el manejador global, para registrar el estado
 * final de la respuesta que recibe el cliente.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class AccessLogFilter implements WebFilter {

    /** Atributo con el llamante ({@code ms01}…{@code ms08}); lo llena RF-GW-08 en las llamadas internas. */
    public static final String ORIGIN_ATTRIBUTE = AccessLogFilter.class.getName() + ".origin";
    static final String EXTERNAL_ORIGIN = "externo";
    static final String GATEWAY_UPSTREAM = "gw";
    /** Estado registrado cuando el cliente se desconecta antes de recibir respuesta. */
    static final int CLIENT_CLOSED_REQUEST = 499;

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private final GatewayErrorWebExceptionHandler errorHandler;
    private final CorrelationIdProvider correlationIdProvider;

    public AccessLogFilter(GatewayErrorWebExceptionHandler errorHandler, CorrelationIdProvider correlationIdProvider) {
        this.errorHandler = errorHandler;
        this.correlationIdProvider = correlationIdProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startNanos = System.nanoTime();
        return chain.filter(exchange)
                .onErrorResume(error -> errorHandler.handle(exchange, error))
                .doOnTerminate(() -> logAccess(exchange, false, startNanos))
                .doOnCancel(() -> logAccess(exchange, true, startNanos));
    }

    private void logAccess(ServerWebExchange exchange, boolean cancelled, long startNanos) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            int status = statusOf(exchange, cancelled);
            log.atLevel(levelFor(status))
                    .addKeyValue("correlation_id", correlationIdProvider.correlationId(exchange))
                    .addKeyValue("method", request.getMethod().name())
                    .addKeyValue("path", request.getPath().value())
                    .addKeyValue("status", status)
                    .addKeyValue("duration_ms", Duration.ofNanos(System.nanoTime() - startNanos).toMillis())
                    .addKeyValue("upstream", upstreamOf(exchange))
                    .addKeyValue("origen", exchange.getAttributeOrDefault(ORIGIN_ATTRIBUTE, EXTERNAL_ORIGIN))
                    .log("Solicitud procesada");
        } catch (RuntimeException e) {
            // Una falla del log nunca altera ni bloquea la respuesta (RF-GW-07, A2).
        }
    }

    private static int statusOf(ServerWebExchange exchange, boolean cancelled) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        if (status != null) {
            return status.value();
        }
        return cancelled ? CLIENT_CLOSED_REQUEST : 200;
    }

    private static Level levelFor(int status) {
        if (status >= 500) {
            return Level.ERROR;
        }
        return status >= 400 ? Level.WARN : Level.INFO;
    }

    /** El MS si la solicitud se le reenvió; {@code gw} si respondió el propio GW. */
    private static Object upstreamOf(ServerWebExchange exchange) {
        return ServerWebExchangeUtils.isAlreadyRouted(exchange) ? RouteConfig.upstreamOf(exchange) : GATEWAY_UPSTREAM;
    }
}
