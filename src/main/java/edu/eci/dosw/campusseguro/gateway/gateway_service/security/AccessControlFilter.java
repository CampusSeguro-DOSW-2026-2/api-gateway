package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Paso 4 del pipeline: exige la credencial de la política de la ruta. Corre solo si la ruta tiene
 * destino (una ruta inexistente sigue siendo 404) y antes de llamar al MS, así que una solicitud
 * rechazada no consume recursos de los MS.
 */
@Component
public class AccessControlFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessPolicyResolver policyResolver;
    private final JwtValidator jwtValidator;

    public AccessControlFilter(AccessPolicyResolver policyResolver, JwtValidator jwtValidator) {
        this.policyResolver = policyResolver;
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            authorize(exchange);
            return chain.filter(exchange);
        });
    }

    private void authorize(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        AccessPolicy policy = policyResolver.resolve(request.getMethod(), request.getPath().value());
        if (policy == AccessPolicy.INTERNA) {
            // La clave de servicio llega con RF-GW-08; hasta entonces ninguna llamada interna se acepta.
            throw new GatewayException(GatewayError.UNAUTHORIZED);
        }
        if (policy == AccessPolicy.PROTEGIDA) {
            exchange.getAttributes().put(AuthenticatedUser.ATTRIBUTE, jwtValidator.validate(bearerToken(request)));
        }
    }

    /** Más de una cabecera {@code Authorization} → 400; ausente o sin esquema Bearer → 401. */
    private static String bearerToken(ServerHttpRequest request) {
        List<String> authorization = request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION);
        if (authorization.size() > 1) {
            throw new GatewayException(GatewayError.VALIDATION);
        }
        String value = authorization.isEmpty() ? "" : authorization.get(0);
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new GatewayException(GatewayError.UNAUTHORIZED);
        }
        return value.substring(BEARER_PREFIX.length()).trim();
    }

    /** Después del registro de desconexión y antes de cualquier filtro que llame al MS. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
