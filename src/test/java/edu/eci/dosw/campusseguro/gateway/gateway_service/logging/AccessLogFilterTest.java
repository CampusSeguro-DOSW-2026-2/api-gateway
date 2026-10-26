package edu.eci.dosw.campusseguro.gateway.gateway_service.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdProvider;
import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.ExchangeAttributeCorrelationIdProvider;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.DownstreamFailureErrorResolver;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.ErrorResponseWriter;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayErrorWebExceptionHandler;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayExceptionErrorResolver;
import edu.eci.dosw.campusseguro.gateway.gateway_service.routing.RouteConfig;
import edu.eci.dosw.campusseguro.gateway.gateway_service.security.CallingService;
import edu.eci.dosw.campusseguro.gateway.gateway_service.support.CapturedLogs;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/** CA-GW-07-01, RN-GW-17 y RF-GW-07 A2. */
class AccessLogFilterTest {

    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private final AccessLogFilter filter = new AccessLogFilter(
            new GatewayErrorWebExceptionHandler(
                    List.of(new GatewayExceptionErrorResolver(), new DownstreamFailureErrorResolver()),
                    new ErrorResponseWriter(JsonMapper.builder().build(), new ExchangeAttributeCorrelationIdProvider())),
            new ExchangeAttributeCorrelationIdProvider());

    private final CapturedLogs logs = CapturedLogs.of(AccessLogFilter.class);

    @AfterEach
    void detach() {
        logs.close();
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CorrelationIdProvider.ATTRIBUTE, CORRELATION_ID);
        return exchange;
    }

    private static void routedToMs01(MockServerWebExchange exchange) {
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, Route.async().id("ms01:/api/v1/users/**")
                .uri("http://ms01:8081").predicate(e -> true).metadata(RouteConfig.UPSTREAM_ATTR, "ms01").build());
        ServerWebExchangeUtils.setAlreadyRouted(exchange);
    }

    private ILoggingEvent onlyEvent() {
        assertThat(logs.events()).hasSize(1);
        return logs.events().get(0);
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }

    @Test
    void shouldLogASuccessfulForwardedRequestAsInfoWithTheMinimumFields() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me?correo=secreto@x.co"));
        WebFilterChain ms01Responds = forwarded -> {
            routedToMs01(exchange);
            forwarded.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, ms01Responds)).verifyComplete();

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(fields(event)).containsOnlyKeys("correlation_id", "method", "path", "status", "duration_ms",
                        "upstream", "origen")
                .containsEntry("correlation_id", CORRELATION_ID)
                .containsEntry("method", "GET")
                .containsEntry("path", "/api/v1/users/me")
                .containsEntry("status", 200)
                .containsEntry("upstream", "ms01")
                .containsEntry("origen", "externo");
    }

    @Test
    void shouldLogGatewayErrorsWithTheFinalStatusAndUpstreamGw() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me"));

        StepVerifier.create(filter.filter(exchange, e -> Mono.error(new GatewayException(GatewayError.UNAUTHORIZED))))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(fields(event)).containsEntry("status", 401).containsEntry("upstream", "gw");
    }

    @Test
    void shouldLogServerErrorsAsErrorWithTheUpstreamThatFailed() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me"));

        StepVerifier.create(filter.filter(exchange, e -> {
            routedToMs01(exchange);
            return Mono.error(new ConnectException("refused"));
        })).verifyComplete();

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(fields(event)).containsEntry("status", 502).containsEntry("upstream", "ms01");
    }

    @Test
    void shouldMeasureTheDurationUntilTheResponseEnds() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me"));

        StepVerifier.create(filter.filter(exchange, e -> Mono.delay(Duration.ofMillis(150)).then())).verifyComplete();

        assertThat((Long) fields(onlyEvent()).get("duration_ms")).isGreaterThanOrEqualTo(150L);
    }

    @Test
    void shouldLogClientDisconnectionAs499() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me"));

        StepVerifier.create(filter.filter(exchange, e -> Mono.never())).thenCancel().verify();

        assertThat(fields(onlyEvent())).containsEntry("status", 499);
    }

    @Test
    void shouldReportTheInternalCallerWhenItIsKnown() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/audit/events"));
        exchange.getAttributes().put(CallingService.ATTRIBUTE, new CallingService("ms04"));

        StepVerifier.create(filter.filter(exchange, e -> Mono.empty())).verifyComplete();

        assertThat(fields(onlyEvent())).containsEntry("origen", "ms04");
    }

    @Test
    void shouldNotChangeTheResponseWhenLoggingFails() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AccessLogFilter.class);
        ch.qos.logback.core.AppenderBase<ILoggingEvent> failing = new ch.qos.logback.core.AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                throw new IllegalStateException("el sistema de logs falló");
            }
        };
        failing.start();
        logger.addAppender(failing);
        try {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me"));

            StepVerifier.create(filter.filter(exchange, e -> {
                e.getResponse().setStatusCode(HttpStatus.OK);
                return Mono.empty();
            })).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            logger.detachAppender(failing);
        }
    }

    @Test
    void shouldNeverLogKeyValuePairsOtherThanTheMinimumFields() {
        StepVerifier.create(filter.filter(exchange(MockServerHttpRequest.post("/api/v1/auth/login")
                .header("Authorization", "Bearer secreto")), e -> Mono.empty())).verifyComplete();

        assertThat(onlyEvent().getKeyValuePairs()).extracting(KeyValuePair::toString)
                .noneMatch(pair -> pair.contains("secreto") || pair.contains("Authorization"));
    }
}
