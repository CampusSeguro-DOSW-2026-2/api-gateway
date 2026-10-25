package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Registra la desconexión del cliente (RF-GW-01, A4). La cancelación se propaga a la llamada
 * downstream, que libera su conexión.
 */
@Component
public class ClientDisconnectLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ClientDisconnectLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doOnCancel(() -> log.info(
                "Cliente desconectado: se cancela la llamada downstream method={} path={} upstream={}",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath(), upstreamOf(exchange)));
    }

    private static Object upstreamOf(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return route == null ? "desconocido" : route.getMetadata().get(RouteConfig.UPSTREAM_ATTR);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
