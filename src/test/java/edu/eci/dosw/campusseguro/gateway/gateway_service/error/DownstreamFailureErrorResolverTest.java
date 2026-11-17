package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DownstreamFailureErrorResolverTest {

    private final DownstreamFailureErrorResolver resolver = new DownstreamFailureErrorResolver();

    @Test
    void shouldMapTimeoutsTo504() {
        assertThat(resolver.resolve(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT))).contains(GatewayError.TIMEOUT);
        assertThat(resolver.resolve(new org.springframework.cloud.gateway.support.TimeoutException("t")))
                .contains(GatewayError.TIMEOUT);
        assertThat(resolver.resolve(new RuntimeException(new TimeoutException()))).contains(GatewayError.TIMEOUT);
        assertThat(resolver.resolve(new ConnectTimeoutException("connect"))).contains(GatewayError.TIMEOUT);
        assertThat(resolver.resolve(ReadTimeoutException.INSTANCE)).contains(GatewayError.TIMEOUT);
    }

    @Test
    void shouldMapUnreachableDestinationTo502() {
        assertThat(resolver.resolve(new ConnectException("refused"))).contains(GatewayError.SERVICE_UNAVAILABLE);
        assertThat(resolver.resolve(new RuntimeException(new UnknownHostException("ms01"))))
                .contains(GatewayError.SERVICE_UNAVAILABLE);
        assertThat(resolver.resolve(new RuntimeException(new SSLHandshakeException("certificate_unknown"))))
                .contains(GatewayError.SERVICE_UNAVAILABLE);
    }

    /**
     * El rechazo del certificado del GW por el MS llega como un IOException simple o como un canal cerrado según el
     * sistema operativo y el momento en que el destino cierra la conexión
     */
    @Test
    void shouldMapAConnectionResetOrClosedByTheDestinationTo502() {
        assertThat(resolver.resolve(new RuntimeException(new IOException("Connection reset by peer"))))
                .contains(GatewayError.SERVICE_UNAVAILABLE);
        assertThat(resolver.resolve(new IOException("Se ha anulado una conexión establecida por el software en su equipo host")))
                .contains(GatewayError.SERVICE_UNAVAILABLE);
        assertThat(resolver.resolve(new RuntimeException(new ClosedChannelException())))
                .contains(GatewayError.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldNotResolveOtherExceptions() {
        assertThat(resolver.resolve(new IllegalStateException("x"))).isEmpty();
        assertThat(resolver.resolve(new ResponseStatusException(HttpStatus.NOT_FOUND))).isEmpty();
    }
}
