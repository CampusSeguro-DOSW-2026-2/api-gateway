package edu.eci.dosw.campusseguro.gateway.gateway_service.correlation;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Lee la correlación que {@link CorrelationIdFilter} dejó en el exchange. Si no existe (p. ej. en un
 * exchange que no pasó por el filtro), genera un UUID v4 y lo guarda para que toda la solicitud use
 * el mismo valor.
 */
@Component
public class ExchangeAttributeCorrelationIdProvider implements CorrelationIdProvider {

    @Override
    public String correlationId(ServerWebExchange exchange) {
        return exchange.getAttributes()
                .computeIfAbsent(ATTRIBUTE, key -> UUID.randomUUID().toString())
                .toString();
    }
}
