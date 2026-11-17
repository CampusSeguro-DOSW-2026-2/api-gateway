package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fallos al llamar al MS destino (RF-GW-01, A2/A3): timeout → 504; cualquier fallo de entrada/salida de la conexión
 * (conexión rechazada, host desconocido, conexión cortada o anulada por el destino, TLS rechazado por mTLS, ADR-006)
 * → 502. El timeout se evalúa primero porque el timeout de conexión también es una {@link IOException}.
 */
@Component
@Order(200)
public class DownstreamFailureErrorResolver implements GatewayErrorResolver {

    @Override
    public Optional<GatewayError> resolve(Throwable exception) {
        if (isTimeout(exception)) {
            return Optional.of(GatewayError.TIMEOUT);
        }
        if (causes(exception).anyMatch(DownstreamFailureErrorResolver::isUnreachable)) {
            return Optional.of(GatewayError.SERVICE_UNAVAILABLE);
        }
        return Optional.empty();
    }

    private static boolean isTimeout(Throwable exception) {
        boolean gatewayTimeoutStatus = exception instanceof ResponseStatusException statusException
                && statusException.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value();
        return gatewayTimeoutStatus || causes(exception).anyMatch(cause ->
                cause instanceof org.springframework.cloud.gateway.support.TimeoutException
                        || cause instanceof java.util.concurrent.TimeoutException
                        || cause instanceof io.netty.channel.ConnectTimeoutException
                        || cause instanceof ReadTimeoutException);
    }

    /**
     * Un mismo fallo de conexión llega con tipos distintos según el sistema operativo y el momento en que el destino
     * cierra: SSLException, PrematureCloseException, NativeIoException (Linux) o un IOException simple del transporte
     * NIO (p. ej. "Connection reset by peer" o la conexión anulada en Windows). Todos son IOException
     */
    private static boolean isUnreachable(Throwable cause) {
        return cause instanceof IOException;
    }

    private static Stream<Throwable> causes(Throwable exception) {
        return Stream.iterate(exception, cause -> cause != null, Throwable::getCause).limit(20);
    }
}
