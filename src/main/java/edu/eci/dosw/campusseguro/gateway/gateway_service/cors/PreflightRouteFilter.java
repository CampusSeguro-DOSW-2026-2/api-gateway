package edu.eci.dosw.campusseguro.gateway.gateway_service.cors;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Un preflight a una ruta sin destino responde {@code 404 NO_ENCONTRADO} (RF-GW-04, A4). Sin este
 * filtro, WebFlux responde un 403 vacío cuando ningún manejador acepta el preflight.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PreflightRouteFilter implements WebFilter {

    private final RouteLocator routeLocator;

    public PreflightRouteFilter(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!CorsUtils.isPreFlightRequest(exchange.getRequest())) {
            return chain.filter(exchange);
        }
        return routeLocator.getRoutes()
                .filterWhen(route -> route.getPredicate().apply(exchange))
                .hasElements()
                .flatMap(routeExists -> Boolean.TRUE.equals(routeExists)
                        ? chain.filter(exchange)
                        : Mono.error(new GatewayException(GatewayError.NOT_FOUND)));
    }
}
