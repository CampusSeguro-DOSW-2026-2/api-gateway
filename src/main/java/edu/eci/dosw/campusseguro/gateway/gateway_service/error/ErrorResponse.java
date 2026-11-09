package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Cuerpo de error de los Estándares §7.1: exactamente estos cinco campos, nunca `null`. */
public record ErrorResponse(
        int status,
        String codigo,
        String mensaje,
        List<FieldError> errores,
        @JsonProperty("correlation_id") String correlationId) {

    public record FieldError(String campo, String mensaje) {
    }
}
