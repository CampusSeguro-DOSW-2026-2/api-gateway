package edu.eci.dosw.campusseguro.gateway.gateway_service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GatewayServiceApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void shouldLoadContextWithMs01RoutesOfTheTable() {
        assertThat(routeLocator.getRoutes().map(Route::getId).collectList().block()).containsExactlyInAnyOrder(
                "ms01:/api/v1/auth/**", "ms01:/api/v1/users", "ms01:/api/v1/users/**", "ms01:/api/v1/roles");
    }
}
