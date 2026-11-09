package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

import static org.springframework.cloud.gateway.support.RouteMetadataUtils.CONNECT_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import edu.eci.dosw.campusseguro.gateway.gateway_service.config.GatewayRoutesProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;

/**
 * Construye las rutas de Spring Cloud Gateway a partir de la tabla de rutas. Cada patrón es una
 * ruta propia ordenada por especificidad. Reenvía método, ruta completa, query params y cuerpo sin
 * cambios (RN-GW-01): sin reescrituras, sin reintentos y sin restringir métodos (RN-GW-04/05).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayRoutesProperties.class)
public class RouteConfig {

    /** Metadato de la ruta con el MS destino (`ms01`…`ms08`). */
    public static final String UPSTREAM_ATTR = "upstream";

    /** MS destino de la solicitud, o {@code desconocido} si no coincidió ninguna ruta. */
    public static Object upstreamOf(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        return route == null ? "desconocido" : route.getMetadata().get(UPSTREAM_ATTR);
    }

    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder, GatewayRoutesProperties properties) {
        int timeoutMs = Math.toIntExact(properties.downstreamTimeout().toMillis());
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (GatewayRoutesProperties.Route route : properties.routes()) {
            for (String path : route.paths()) {
                routes.route(route.id() + ":" + path, spec -> spec
                        .order(PathSpecificity.orderOf(path))
                        .path(path)
                        .metadata(UPSTREAM_ATTR, route.id())
                        .metadata(CONNECT_TIMEOUT_ATTR, timeoutMs)
                        .metadata(RESPONSE_TIMEOUT_ATTR, (long) timeoutMs)
                        .uri(route.uri()));
            }
        }
        return routes.build();
    }
}
