package edu.eci.dosw.campusseguro.gateway.gateway_service.cors;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.server.ServerWebExchange;

/**
 * Procesador CORS de Spring con dos ajustes a RF-GW-04:
 * <ul>
 *   <li>Preflight no permitido (origen, método o cabeceras): {@code 403 PROHIBIDO} con
 *       {@code ErrorResponse}, no el 403 vacío por defecto (A1).</li>
 *   <li>Solicitud real con origen no permitido: se procesa sin cabeceras CORS y el navegador
 *       bloquea la lectura, en lugar de rechazarla (A2).</li>
 * </ul>
 */
class GatewayCorsProcessor extends DefaultCorsProcessor {

    @Override
    public boolean process(CorsConfiguration configuration, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        if (configuration != null && !CorsUtils.isPreFlightRequest(request) && CorsUtils.isCorsRequest(request)
                && configuration.checkOrigin(request.getHeaders().getOrigin()) == null) {
            return true;
        }
        return super.process(configuration, exchange);
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response) {
        response.getHeaders().remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        throw new GatewayException(GatewayError.FORBIDDEN);
    }
}
