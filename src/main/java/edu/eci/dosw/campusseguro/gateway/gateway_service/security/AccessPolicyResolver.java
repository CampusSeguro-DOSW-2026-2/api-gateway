package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/** Paso 4 del pipeline: clasifica una solicitud por método + ruta exacta (RN-GW-08). */
@Component
public class AccessPolicyResolver {

    private final List<AccessPolicyProperties.Endpoint> publicRoutes;
    private final List<AccessPolicyProperties.Endpoint> internalRoutes;

    public AccessPolicyResolver(AccessPolicyProperties properties) {
        this.publicRoutes = List.copyOf(properties.publicRoutes());
        this.internalRoutes = List.copyOf(properties.internalRoutes());
    }

    public AccessPolicy resolve(HttpMethod method, String path) {
        if (matches(publicRoutes, method, path)) {
            return AccessPolicy.PUBLICA;
        }
        if (matches(internalRoutes, method, path)) {
            return AccessPolicy.INTERNA;
        }
        return AccessPolicy.PROTEGIDA;
    }

    private static boolean matches(List<AccessPolicyProperties.Endpoint> endpoints, HttpMethod method, String path) {
        return endpoints.stream().anyMatch(endpoint -> endpoint.method().equals(method) && endpoint.path().equals(path));
    }
}
