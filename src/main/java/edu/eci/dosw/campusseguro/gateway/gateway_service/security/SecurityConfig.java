package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AccessPolicyProperties.class, JwtProperties.class, ServiceKeyProperties.class})
public class SecurityConfig {
}
