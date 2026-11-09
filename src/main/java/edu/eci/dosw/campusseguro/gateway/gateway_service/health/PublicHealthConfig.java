package edu.eci.dosw.campusseguro.gateway.gateway_service.health;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.Map;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * {@code GET /actuator/health} público en el puerto externo (RF-GW-07, Estándares §11): solo
 * {@code {"status":"UP"}} o {@code {"status":"DOWN"}} (503), sin detalles y sin consultar a los MS.
 * El resto de Actuator vive únicamente en el puerto de gestión.
 */
@Configuration(proxyBeanMethods = false)
public class PublicHealthConfig {

    @Bean
    RouterFunction<ServerResponse> publicHealthRoute(HealthEndpoint healthEndpoint) {
        return route(GET("/actuator/health"), request -> Mono
                .fromCallable(() -> healthEndpoint.health().getStatus())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(status -> ServerResponse
                        .status(Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("status", Status.UP.equals(status) ? Status.UP.getCode() : Status.DOWN.getCode()))));
    }
}
