package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import java.util.Optional;

/**
 * Traduce una excepción a un error del GW. Cada caso nuevo se agrega como otra implementación, sin
 * modificar el manejador global. Se evalúan según su {@code @Order}; gana la primera que resuelve.
 */
public interface GatewayErrorResolver {

    Optional<GatewayError> resolve(Throwable exception);
}
