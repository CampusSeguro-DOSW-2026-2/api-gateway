package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import io.netty.handler.timeout.ReadTimeoutException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.http.client.PrematureCloseException;

/**
 * Fallos al llamar al MS destino (RF-GW-01, A2/A3): timeout → 504; conexión rechazada, host
 * desconocido o conexión cortada → 502. El timeout se evalúa primero porque el timeout de conexión
 * también es una {@link SocketException}.
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

    private static boolean isUnreachable(Throwable cause) {
        return cause instanceof SocketException
                || cause instanceof UnknownHostException
                || cause instanceof PrematureCloseException;
    }

    private static Stream<Throwable> causes(Throwable exception) {
        return Stream.iterate(exception, cause -> cause != null, Throwable::getCause).limit(20);
    }
}
