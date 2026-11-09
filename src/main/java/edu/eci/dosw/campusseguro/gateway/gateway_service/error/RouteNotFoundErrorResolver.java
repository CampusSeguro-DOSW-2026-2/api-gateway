package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Ninguna ruta coincide: 404 sin invocar a ningún MS (RF-GW-01, A1). */
@Component
@Order(100)
public class RouteNotFoundErrorResolver implements GatewayErrorResolver {

    @Override
    public Optional<GatewayError> resolve(Throwable exception) {
        if (exception instanceof ResponseStatusException statusException
                && statusException.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            return Optional.of(GatewayError.NOT_FOUND);
        }
        return Optional.empty();
    }
}
