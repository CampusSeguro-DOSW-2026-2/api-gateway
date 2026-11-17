package edu.eci.dosw.campusseguro.gateway.gateway_service;

import static org.assertj.core.api.Assertions.assertThat;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.GatewayIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

class GatewayServiceApplicationTests extends GatewayIntegrationTest {

    @Autowired
    private RouteLocator routeLocator;

    /**
     * El contexto carga con las rutas de la tabla
     */
    @Test
    void shouldLoadContextWithTheRoutesOfTheTable() {
        assertThat(routeLocator.getRoutes().map(Route::getId).collectList().block()).containsExactlyInAnyOrder(
                "ms01:/api/v1/auth/**", "ms01:/api/v1/users", "ms01:/api/v1/users/**", "ms01:/api/v1/roles",
                "ms01:/api/v1/roles/**", "ms01:/api/v1/permissions",
                "ms02:/api/v1/buildings", "ms02:/api/v1/buildings/**", "ms02:/api/v1/floors/**",
                "ms02:/api/v1/spaces/**", "ms02:/api/v1/safe-zones", "ms02:/api/v1/safe-zones/**",
                "ms02:/api/v1/evacuation-routes", "ms02:/api/v1/evacuation-routes/**",
                "ms02:/api/v1/preventive-recommendations", "ms02:/api/v1/preventive-recommendations/**",
                "ms02:/api/v1/campuses", "ms02:/api/v1/campuses/**", "ms02:/api/v1/open-zones/**",
                "ms02:/api/v1/exits/**", "ms02:/api/v1/stairs/**", "ms02:/api/v1/public/**",
                "ms02:/api/v1/images", "ms02:/api/v1/images/**",
                "ms03:/api/v1/alertas", "ms03:/api/v1/alertas/**",
                "ms04:/api/v1/chi/**",
                "ms05:/api/v1/brigadistas/**", "ms05:/api/v1/incidentes/**", "ms05:/api/v1/bei/**",
                "ms05:/api/v1/asignaciones/**", "ms05:/api/v1/responsabilidades/**",
                "ms06:/api/v1/eventos", "ms06:/api/v1/eventos/**",
                "ms06:/api/v1/tipos-evento", "ms06:/api/v1/tipos-evento/**",
                "ms08:/api/v1/analitica/**", "ms08:/api/v1/auditoria/**", "ms08:/api/v1/audit/events");
    }
}
