package edu.eci.dosw.campusseguro.gateway.gateway_service.cors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.context.annotation.Configuration;

/**
 * Paso 3 del pipeline (RF-GW-04). Usa el soporte CORS del mapeo de rutas del gateway: corre después
 * de resolver la ruta (un preflight a una ruta inexistente es 404) y antes de cualquier filtro, así
 * que el preflight se responde sin exigir JWT y sin llamar al MS.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    public CorsConfig(GlobalCorsProperties globalCors, RoutePredicateHandlerMapping routeMapping,
            CorsProperties properties) {
        // GlobalCorsProperties es la fuente que el gateway vuelve a aplicar cada vez que refresca las rutas.
        globalCors.getCorsConfigurations().put("/**", properties.toCorsConfiguration());
        routeMapping.setCorsConfigurations(globalCors.getCorsConfigurations());
        routeMapping.setCorsProcessor(new GatewayCorsProcessor());
    }
}
