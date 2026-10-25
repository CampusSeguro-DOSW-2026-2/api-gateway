package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
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
    }

    @Test
    void shouldNotResolveOtherExceptions() {
        assertThat(resolver.resolve(new IllegalStateException("x"))).isEmpty();
        assertThat(resolver.resolve(new ResponseStatusException(HttpStatus.NOT_FOUND))).isEmpty();
    }
}
