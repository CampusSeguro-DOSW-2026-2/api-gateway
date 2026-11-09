package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Errores lanzados explícitamente por el GW con {@link GatewayException}. */
@Component
@Order(0)
public class GatewayExceptionErrorResolver implements GatewayErrorResolver {

    @Override
    public Optional<GatewayError> resolve(Throwable exception) {
        return exception instanceof GatewayException gatewayException
                ? Optional.of(gatewayException.error())
                : Optional.empty();
    }
}
