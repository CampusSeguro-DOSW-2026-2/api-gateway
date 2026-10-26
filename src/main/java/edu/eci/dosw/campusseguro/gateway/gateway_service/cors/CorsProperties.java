package edu.eci.dosw.campusseguro.gateway.gateway_service.cors;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Política CORS única de CampusSeguro (Estándares §2.1, GW-C §5). Los orígenes, las credenciales y
 * la vigencia son configuración; los métodos y las cabeceras son fijos. Las cabeceras internas
 * ({@code X-User-*}, {@code X-Service-*}) nunca se permiten al navegador (RN-GW-13).
 */
@Validated
@ConfigurationProperties(prefix = "gateway.cors")
public record CorsProperties(
        @NotEmpty(message = "CORS_ALLOWED_ORIGINS es obligatorio")
        List<@NotNull @Pattern(regexp = "^(\\*|https?://[^/\\s]+)$",
                message = "cada origen de CORS_ALLOWED_ORIGINS debe ser esquema://host[:puerto], sin ruta ni barra final")
                String> allowedOrigins,
        boolean allowCredentials,
        @NotNull(message = "CORS_MAX_AGE_SECONDS es obligatorio") Duration maxAge) {

    static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    static final List<String> ALLOWED_HEADERS =
            List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, "X-Correlation-ID");
    static final List<String> EXPOSED_HEADERS = List.of("X-Correlation-ID");

    @AssertTrue(message = "CORS_ALLOWED_ORIGINS no puede incluir * si CORS_ALLOW_CREDENTIALS=true")
    public boolean isWildcardWithoutCredentials() {
        return !allowCredentials || allowedOrigins == null || !allowedOrigins.contains(CorsConfiguration.ALL);
    }

    @AssertTrue(message = "CORS_MAX_AGE_SECONDS no puede ser negativo")
    public boolean isMaxAgeValid() {
        return maxAge == null || !maxAge.isNegative();
    }

    CorsConfiguration toCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);
        return configuration;
    }
}
