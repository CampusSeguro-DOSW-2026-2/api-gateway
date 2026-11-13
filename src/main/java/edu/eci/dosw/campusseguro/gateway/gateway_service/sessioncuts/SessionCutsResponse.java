package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Respuesta de GET /api/v1/internal/session-cuts de MS01: la instantánea de los cortes de sesión vigentes
 *
 * @param generatedAt instante en que MS01 armó la respuesta
 * @param windowSeconds ventana en segundos dentro de la cual un corte sigue vigente
 * @param usuarios cortes de sesión por usuario
 * @param roles cortes de sesión por rol
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionCutsResponse(
        @JsonProperty("generado_en") String generatedAt,
        @JsonProperty("ventana_segundos") Long windowSeconds,
        List<UserCut> usuarios,
        List<RoleCut> roles) {

    /**
     * Corte de sesión de un usuario
     *
     * @param id id del usuario
     * @param validFrom instante desde el cual son válidas sus credenciales, en ISO 8601 UTC
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserCut(String id, @JsonProperty("valido_desde") String validFrom) {
    }

    /**
     * Corte de sesión de un rol
     *
     * @param rol nombre del rol
     * @param validFrom instante desde el cual son válidas las credenciales de sus usuarios, en ISO 8601 UTC
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoleCut(String rol, @JsonProperty("valido_desde") String validFrom) {
    }
}
