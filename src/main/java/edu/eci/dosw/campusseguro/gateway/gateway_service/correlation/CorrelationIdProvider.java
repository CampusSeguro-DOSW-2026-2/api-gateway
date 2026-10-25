package edu.eci.dosw.campusseguro.gateway.gateway_service.correlation;

import org.springframework.web.server.ServerWebExchange;

/** Entrega el `X-Correlation-ID` de la solicitud en curso (RF-GW-06). */
public interface CorrelationIdProvider {

    /** Atributo del exchange donde vive la correlación de la solicitud. */
    String ATTRIBUTE = CorrelationIdProvider.class.getName() + ".correlationId";

    String correlationId(ServerWebExchange exchange);
}
