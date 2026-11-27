package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activa la configuración de la consulta de cortes de sesión
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SessionCutsProperties.class)
public class SessionCutsConfig {
}
