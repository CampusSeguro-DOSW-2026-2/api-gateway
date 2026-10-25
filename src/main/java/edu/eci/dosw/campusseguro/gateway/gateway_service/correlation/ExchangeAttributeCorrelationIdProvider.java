package edu.eci.dosw.campusseguro.gateway.gateway_service.correlation;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Lee la correlación del atributo del exchange. Si aún no existe (el filtro de RF-GW-06 no se ha
 * ejecutado), genera un UUID v4 y lo guarda para que toda la solicitud use el mismo valor.
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
