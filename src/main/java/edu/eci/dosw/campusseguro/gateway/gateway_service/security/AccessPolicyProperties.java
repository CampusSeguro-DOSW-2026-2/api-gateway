package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;

/**
 * Rutas públicas e internas por método + ruta exacta (GW-C §3). Lo no listado es protegido.
 * Los comodines están prohibidos: una configuración con comodines impide el arranque.
 */
@Validated
@ConfigurationProperties(prefix = "gateway.access")
public record AccessPolicyProperties(
        @NotNull List<@Valid @NotNull Endpoint> publicRoutes,
        @NotNull List<@Valid @NotNull Endpoint> internalRoutes) {

    public record Endpoint(
            @NotNull(message = "el método HTTP es obligatorio") HttpMethod method,
            @NotNull @Pattern(regexp = "^/[^*?{}\\s]*$", message = "la ruta debe ser exacta, sin comodines") String path) {
    }
}
