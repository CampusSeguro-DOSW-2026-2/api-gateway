package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la consulta periódica de cortes de sesión a MS01 (Estándares §1, §3 y §5)
 *
 * @param url dirección de la ruta interna de cortes de sesión de MS01 (SESSION_CUTS_URL)
 * @param interval tiempo entre consultas (SESSION_CUTS_INTERVAL_SECONDS)
 * @param timeout tiempo máximo de espera de cada consulta (SESSION_CUTS_TIMEOUT_SECONDS)
 */
@Validated
@ConfigurationProperties(prefix = "gateway.session-cuts")
public record SessionCutsProperties(
        @NotNull(message = "SESSION_CUTS_URL es obligatorio") URI url,
        @NotNull(message = "SESSION_CUTS_INTERVAL_SECONDS es obligatorio") Duration interval,
        @NotNull(message = "SESSION_CUTS_TIMEOUT_SECONDS es obligatorio") Duration timeout) {

    /**
     * Indica si la dirección de consulta es una URI http o https absoluta
     *
     * @return true si es válida o si no se configuró (lo informa la validación de obligatorio)
     */
    @AssertTrue(message = "SESSION_CUTS_URL debe ser una URI http(s) absoluta")
    public boolean isUrlValid() {
        if (url == null) {
            return true;
        }
        boolean httpScheme = "http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme());
        return httpScheme && url.getHost() != null;
    }

    /**
     * Indica si el intervalo y el tiempo de espera son mayores que cero
     *
     * @return true si ambos son positivos o si alguno no se configuró (lo informa la validación de obligatorio)
     */
    @AssertTrue(message = "SESSION_CUTS_INTERVAL_SECONDS y SESSION_CUTS_TIMEOUT_SECONDS deben ser mayores que 0")
    public boolean isDurationsValid() {
        if (interval == null || timeout == null) {
            return true;
        }
        return interval.isPositive() && timeout.isPositive();
    }
}
