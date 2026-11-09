package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regla del 502 (Estándares §7.3): un error 4xx/5xx de un MS es válido si es un JSON con exactamente
 * los cinco campos de {@link ErrorResponse}, {@code status} igual al código HTTP y
 * {@code correlation_id} igual al de la solicitud.
 */
@Component
public class DownstreamErrorValidator {

    private static final List<String> FIELDS = List.of("status", "codigo", "mensaje", "errores", "correlation_id");

    private final JsonMapper jsonMapper;

    public DownstreamErrorValidator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** @return el motivo del incumplimiento, o vacío si el error es válido. Nunca incluye el cuerpo. */
    public Optional<String> violation(int httpStatus, byte[] body, String correlationId) {
        JsonNode json;
        try {
            json = jsonMapper.readTree(body);
        } catch (JacksonException e) {
            return Optional.of("cuerpo no JSON");
        }
        if (json == null || !json.isObject()) {
            return Optional.of("cuerpo no es un objeto JSON");
        }
        Set<String> names = new HashSet<>(json.propertyNames());
        for (String field : FIELDS) {
            if (!names.remove(field)) {
                return Optional.of("campo faltante: " + field);
            }
        }
        if (!names.isEmpty()) {
            return Optional.of("campo adicional: " + names.iterator().next());
        }
        if (!json.get("status").isInt() || json.get("status").intValue() != httpStatus) {
            return Optional.of("status distinto del código HTTP");
        }
        if (!json.get("codigo").isString() || !json.get("mensaje").isString()) {
            return Optional.of("codigo o mensaje no son texto");
        }
        if (!json.get("errores").isArray()) {
            return Optional.of("errores no es una lista");
        }
        if (!json.get("correlation_id").isString() || !json.get("correlation_id").stringValue().equals(correlationId)) {
            return Optional.of("correlation_id distinto del de la solicitud");
        }
        return Optional.empty();
    }
}
