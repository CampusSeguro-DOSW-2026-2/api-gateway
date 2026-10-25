package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** RF-GW-01, A4: si el cliente se desconecta, la llamada downstream se cancela y se registra. */
@ExtendWith(OutputCaptureExtension.class)
class ClientDisconnectLoggingFilterTest {

    private final ClientDisconnectLoggingFilter filter = new ClientDisconnectLoggingFilter();

    @Test
    void shouldCancelDownstreamCallAndLogWhenClientDisconnects(CapturedOutput output) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users"));
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, Route.async()
                .id("ms01:/api/v1/users").uri("http://ms01:8081").predicate(e -> true)
                .metadata(RouteConfig.UPSTREAM_ATTR, "ms01").build());
        AtomicBoolean downstreamCancelled = new AtomicBoolean();

        StepVerifier.create(filter.filter(exchange, e -> Mono.<Void>never().doOnCancel(() -> downstreamCancelled.set(true))))
                .thenCancel()
                .verify();

        assertThat(downstreamCancelled).isTrue();
        assertThat(output).contains("Cliente desconectado").contains("upstream=ms01");
    }
}
