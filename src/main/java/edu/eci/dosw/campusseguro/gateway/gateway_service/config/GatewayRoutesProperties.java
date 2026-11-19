package edu.eci.dosw.campusseguro.gateway.gateway_service.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tabla de rutas del GW (GW-C §2 y §5). Se valida al arrancar: una configuración inválida impide el
 * arranque (RN-GW-02, CA-GW-01-05).
 */
@Validated
@ConfigurationProperties(prefix = "gateway")
public record GatewayRoutesProperties(
        @NotNull(message = "GATEWAY_DOWNSTREAM_TIMEOUT_MS es obligatorio") Duration downstreamTimeout,
        @NotEmpty(message = "la tabla de rutas no puede estar vacía") List<@Valid @NotNull Route> routes,
        List<@Valid @NotNull PathTimeout> pathTimeouts) {

    /**
     * Tiempo máximo de respuesta de un prefijo: el propio si está en {@code path-timeouts}, si no el general.
     *
     * @param path prefijo de la tabla de rutas
     * @return tiempo máximo de espera de la respuesta del MS
     */
    public Duration responseTimeoutOf(String path) {
        return pathTimeouts == null ? downstreamTimeout : pathTimeouts.stream()
                .filter(t -> t != null && path.equals(t.path()))
                .map(PathTimeout::timeout)
                .findFirst()
                .orElse(downstreamTimeout);
    }

    @AssertTrue(message = "GATEWAY_DOWNSTREAM_TIMEOUT_MS debe ser mayor que 0")
    public boolean isDownstreamTimeoutPositive() {
        return downstreamTimeout == null || downstreamTimeout.isPositive();
    }

    @AssertTrue(message = "hay ids de ruta duplicados")
    public boolean isRouteIdsUnique() {
        return routes == null || allUnique(routes.stream().filter(r -> r != null).map(Route::id).toList());
    }

    @AssertTrue(message = "hay prefijos duplicados en la tabla de rutas")
    public boolean isPathsUnique() {
        return routes == null || allUnique(routes.stream()
                .filter(r -> r != null && r.paths() != null)
                .flatMap(r -> r.paths().stream())
                .toList());
    }

    @AssertTrue(message = "todo prefijo de path-timeouts debe estar en la tabla de rutas y aparecer una sola vez")
    public boolean isPathTimeoutsDeclared() {
        if (pathTimeouts == null || routes == null) {
            return true;
        }
        Set<String> declared = new HashSet<>();
        routes.stream().filter(r -> r != null && r.paths() != null).forEach(r -> declared.addAll(r.paths()));
        List<String> paths = pathTimeouts.stream().filter(t -> t != null && t.path() != null)
                .map(PathTimeout::path).toList();
        return declared.containsAll(paths) && allUnique(paths);
    }

    private static boolean allUnique(List<String> values) {
        Set<String> seen = new HashSet<>();
        return values.stream().allMatch(seen::add);
    }

    /**
     * Destino de un MS y los patrones de ruta que se le reenvían.
     *
     * @param id    nombre corto del MS (`ms01`…`ms08`), usado como `upstream`
     * @param uri   destino interno (`MSnn_URI`), solo esquema, host y puerto
     * @param paths patrones bajo `/api/v1/` publicados en el swagger del MS (RN-GW-03)
     */
    public record Route(
            @NotBlank(message = "el id de la ruta es obligatorio") String id,
            @NotNull(message = "el destino (MSnn_URI) es obligatorio") URI uri,
            @NotEmpty(message = "la ruta debe declarar al menos un prefijo")
            List<@NotNull @Pattern(regexp = "^/api/v1/\\S+$", message = "todo prefijo debe estar bajo /api/v1/") String> paths) {

        @AssertTrue(message = "el destino debe ser una URI http(s) absoluta, sin ruta ni query (p. ej. http://ms01-identity-and-access:8081)")
        public boolean isUriValid() {
            if (uri == null) {
                return true;
            }
            boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            boolean noPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath());
            return httpScheme && uri.getHost() != null && noPath && uri.getRawQuery() == null;
        }
    }

    /**
     * Tiempo de respuesta propio de un prefijo, para el MS que tarda más que el general (p. ej. el tutor de MS07,
     * que consulta un modelo de lenguaje). El tiempo de conexión sigue siendo el general.
     *
     * @param path    prefijo tal como aparece en la tabla de rutas
     * @param timeout tiempo máximo de espera de la respuesta
     */
    public record PathTimeout(
            @NotNull(message = "el prefijo de path-timeouts es obligatorio") String path,
            @NotNull(message = "el tiempo de path-timeouts es obligatorio") Duration timeout) {

        @AssertTrue(message = "todo tiempo de path-timeouts debe ser mayor que 0")
        public boolean isTimeoutPositive() {
            return timeout == null || timeout.isPositive();
        }
    }
}
